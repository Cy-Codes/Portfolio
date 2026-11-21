package codes.cy.portfolio.server.admin

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.AdminSessionDto
import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.PresignUploadRequest
import codes.cy.portfolio.core.model.PresignUploadResponse
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.StatsOverviewDto
import codes.cy.portfolio.core.model.StatsTimeRange
import codes.cy.portfolio.core.model.UpdateProfileRequest
import codes.cy.portfolio.core.model.UpsertCertificationRequest
import codes.cy.portfolio.core.model.UpsertEducationRequest
import codes.cy.portfolio.core.model.UpsertExperienceRequest
import codes.cy.portfolio.core.model.UpsertProjectRequest
import codes.cy.portfolio.core.model.UpsertSkillRequest
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.serialization.PortfolioJson
import codes.cy.portfolio.server.admin.application.AdminAppModules
import codes.cy.portfolio.server.admin.application.AdminDocumentCommandService
import codes.cy.portfolio.server.admin.application.AdminPortfolioCommandService
import codes.cy.portfolio.server.admin.application.AdminPortfolioQueryService
import codes.cy.portfolio.server.admin.application.AdminStatsQueryService
import codes.cy.portfolio.server.admin.application.ReadinessChecker
import codes.cy.portfolio.server.admin.domain.AdminServerConfig
import codes.cy.portfolio.server.admin.infrastructure.DefaultAdminAuthenticator
import codes.cy.portfolio.server.db.config.SchemaReadinessStatus
import codes.cy.portfolio.server.db.errors.ConflictException
import codes.cy.portfolio.server.db.repositories.AnalyticsQueryRepository
import codes.cy.portfolio.server.db.repositories.DocumentCommandRepository
import codes.cy.portfolio.server.db.repositories.DocumentQueryRepository
import codes.cy.portfolio.server.db.repositories.PortfolioCommandRepository
import codes.cy.portfolio.server.db.repositories.PortfolioQueryRepository
import codes.cy.portfolio.server.db.repositories.ProjectCommandRepository
import codes.cy.portfolio.server.db.repositories.ProjectQueryRepository
import codes.cy.portfolio.server.db.repositories.WriteupCommandRepository
import codes.cy.portfolio.server.db.repositories.WriteupQueryRepository
import codes.cy.portfolio.server.db.storage.BlobStorage
import codes.cy.portfolio.server.db.storage.MimeMagicBytes
import codes.cy.portfolio.server.db.storage.PresignedPutObject
import codes.cy.portfolio.server.db.storage.StoredObjectHead
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AdminServerContractTest {

    @Test
    fun `health probes stay unauthenticated while admin routes enforce JWT and allowlist rules`() = testApplication {
        val harness = TestAdminHarness()
        application { adminModule(harness.modules()) }
        val jsonClient = createClient { install(ContentNegotiation) { json(PortfolioJson) } }

        assertEquals(HttpStatusCode.OK, jsonClient.get("/health").status)
        assertEquals(HttpStatusCode.OK, jsonClient.get("/health/ready").status)

        val unauthenticated = jsonClient.get("/api/v1/admin/auth/session")
        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertIs<ApiError.Unauthorized>(PortfolioJson.decodeFromString<ApiError>(unauthenticated.bodyAsText()))

        val viewerToken = signJwt(subject = "dev-viewer", role = "viewer")
        val forbiddenResp = jsonClient.get("/api/v1/admin/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenResp.status)
        assertIs<ApiError.Forbidden>(PortfolioJson.decodeFromString<ApiError>(forbiddenResp.bodyAsText()))

        val ownerToken = signJwt(subject = "dev-owner", role = "owner")
        val sessionResp = jsonClient.get("/api/v1/admin/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, sessionResp.status)
        val session = sessionResp.body<AdminSessionDto>()
        assertEquals("dev-owner", session.subject)
        assertEquals("owner", session.role)
        assertEquals(DefaultAdminAuthenticator.AUTH_SOURCE_DEV_JWT, session.authSource)
    }

    @Test
    fun `production profile rejects local dev-token JWTs`() = testApplication {
        val harness = TestAdminHarness()
        application { adminModule(harness.modules(appEnv = "prod")) }

        val devToken = signJwt(subject = "dev-owner", role = "owner")
        val resp = client.get("/api/v1/admin/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $devToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `CMS CRUD, conflict mapping, and CWE-190 pagination work with proper HTTP codes`() = testApplication {
        val harness = TestAdminHarness()
        application { adminModule(harness.modules()) }
        val jsonClient = createClient { install(ContentNegotiation) { json(PortfolioJson) } }
        val ownerToken = signJwt(subject = "dev-owner", role = "owner")

        val profileResp = jsonClient.put("/api/v1/admin/profile") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest("CY", "Principal Engineer", "# Bio"))
        }
        assertEquals(HttpStatusCode.OK, profileResp.status)
        assertEquals("CY", profileResp.body<ProfileDto>().fullName)

        val createSkillResp = jsonClient.post("/api/v1/admin/skills") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpsertSkillRequest("Kotlin", "Languages", isFeatured = true, sortOrder = 1))
        }
        assertEquals(HttpStatusCode.Created, createSkillResp.status)
        val createdSkill = createSkillResp.body<SkillDto>()
        assertEquals("/api/v1/admin/skills/${createdSkill.id}", createSkillResp.headers[HttpHeaders.Location])

        val updateSkillResp = jsonClient.put("/api/v1/admin/skills/${createdSkill.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpsertSkillRequest("Kotlin 2.4", "Languages", isFeatured = true, sortOrder = 1))
        }
        assertEquals(HttpStatusCode.OK, updateSkillResp.status)
        assertEquals("Kotlin 2.4", updateSkillResp.body<SkillDto>().name)

        val deleteSkillResp = jsonClient.delete("/api/v1/admin/skills/${createdSkill.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteSkillResp.status)

        val deleteMissingResp = jsonClient.delete("/api/v1/admin/skills/${createdSkill.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NotFound, deleteMissingResp.status)

        val createProjectResp = jsonClient.post("/api/v1/admin/projects") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                UpsertProjectRequest(
                    slug = "first-project",
                    title = "First Project",
                    summary = "Summary",
                    caseStudyMarkdown = "# Study",
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, createProjectResp.status)

        val duplicateProjectResp = jsonClient.post("/api/v1/admin/projects") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                UpsertProjectRequest(
                    slug = "first-project",
                    title = "Duplicate Slug",
                    summary = "Summary",
                    caseStudyMarkdown = "# Study",
                ),
            )
        }
        assertEquals(HttpStatusCode.Conflict, duplicateProjectResp.status)
        assertIs<ApiError.Conflict>(PortfolioJson.decodeFromString<ApiError>(duplicateProjectResp.bodyAsText()))

        val invalidSlugResp = jsonClient.post("/api/v1/admin/writeups") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                UpsertWriteupRequest(
                    slug = "Invalid Slug!",
                    title = "Bad Writeup",
                    summary = "Summary",
                    bodyMarkdown = "Body",
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidSlugResp.status)
        assertIs<ApiError.ValidationError>(PortfolioJson.decodeFromString<ApiError>(invalidSlugResp.bodyAsText()))

        val overflowResp = jsonClient.get("/api/v1/admin/writeups?page=21474838&pageSize=100") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.BadRequest, overflowResp.status)
    }

    @Test
    fun `documents presign, magic-byte validation on confirm, delete, and stats endpoints work cleanly`() = testApplication {
        val harness = TestAdminHarness()
        application { adminModule(harness.modules()) }
        val jsonClient = createClient { install(ContentNegotiation) { json(PortfolioJson) } }
        val ownerToken = signJwt(subject = "dev-owner", role = "owner")

        val disallowedMimeResp = jsonClient.post("/api/v1/admin/documents/presign") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                PresignUploadRequest(
                    originalFilename = "malware.exe",
                    mimeType = "application/x-msdownload",
                    sizeBytes = 1024L,
                    sha256 = "c".repeat(64),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, disallowedMimeResp.status)

        val presignResp = jsonClient.post("/api/v1/admin/documents/presign") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                PresignUploadRequest(
                    originalFilename = "resume.pdf",
                    mimeType = MimeMagicBytes.MIME_PDF,
                    sizeBytes = 128L,
                    sha256 = "d".repeat(64),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, presignResp.status)
        val presigned = presignResp.body<PresignUploadResponse>()

        // Missing in storage before upload -> 400
        val confirmBeforeUploadResp = jsonClient.post("/api/v1/admin/documents/confirm") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                ConfirmUploadRequest(
                    documentId = presigned.documentId,
                    storageKey = presigned.storageKey,
                    title = "Resume",
                    category = DocumentCategory.RESUME,
                    originalFilename = "resume.pdf",
                    declaredMimeType = MimeMagicBytes.MIME_PDF,
                    sha256 = "d".repeat(64),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, confirmBeforeUploadResp.status)

        // Spoofed executable magic bytes under declared application/pdf -> 400 + object deleted
        harness.storedObjects[presigned.storageKey] = byteArrayOf(0x4D, 0x5A, 0x00, 0x00)
        val spoofedConfirmResp = jsonClient.post("/api/v1/admin/documents/confirm") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                ConfirmUploadRequest(
                    documentId = presigned.documentId,
                    storageKey = presigned.storageKey,
                    title = "Resume",
                    category = DocumentCategory.RESUME,
                    originalFilename = "resume.pdf",
                    declaredMimeType = MimeMagicBytes.MIME_PDF,
                    sha256 = "d".repeat(64),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, spoofedConfirmResp.status)
        assertFalse(harness.storedObjects.containsKey(presigned.storageKey))

        // Valid %PDF-1.4 payload -> 201 Created + Location
        harness.storedObjects[presigned.storageKey] = "%PDF-1.4\n%Valid\n".toByteArray(StandardCharsets.US_ASCII)
        val validConfirmResp = jsonClient.post("/api/v1/admin/documents/confirm") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                ConfirmUploadRequest(
                    documentId = presigned.documentId,
                    storageKey = presigned.storageKey,
                    title = "Resume",
                    category = DocumentCategory.RESUME,
                    originalFilename = "resume.pdf",
                    declaredMimeType = MimeMagicBytes.MIME_PDF,
                    sha256 = "d".repeat(64),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, validConfirmResp.status)
        val confirmedDoc = validConfirmResp.body<DocumentDto>()
        assertEquals("/api/v1/admin/documents/${confirmedDoc.id}", validConfirmResp.headers[HttpHeaders.Location])

        val deleteDocResp = jsonClient.delete("/api/v1/admin/documents/${confirmedDoc.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteDocResp.status)
        assertFalse(harness.storedObjects.containsKey(presigned.storageKey))

        val statsResp = jsonClient.get("/api/v1/admin/stats/overview?range=30d") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, statsResp.status)
        assertEquals(StatsTimeRange.THIRTY_DAYS, statsResp.body<StatsOverviewDto>().range)

        val badRangeResp = jsonClient.get("/api/v1/admin/stats/overview?range=999d") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.BadRequest, badRangeResp.status)
    }

    private fun signJwt(
        subject: String,
        role: String,
        secret: String = AdminServerConfig.DEFAULT_DEV_JWT_SECRET,
    ): String {
        val nowMs = System.currentTimeMillis()
        return JWT.create()
            .withSubject(subject)
            .withClaim("role", role)
            .withIssuer(DefaultAdminAuthenticator.DEV_ISSUER)
            .withAudience(DefaultAdminAuthenticator.ADMIN_AUDIENCE)
            .withIssuedAt(Date(nowMs))
            .withExpiresAt(Date(nowMs + 3_600_000L))
            .sign(Algorithm.HMAC256(secret))
    }
}

private class TestAdminHarness {
    val fixedNow: Instant = Instant.parse("2026-11-20T12:00:00Z")
    val readiness: AtomicReference<SchemaReadinessStatus> = AtomicReference(
        SchemaReadinessStatus(ready = true, appliedMigrationCount = 2),
    )
    val storedObjects: MutableMap<String, ByteArray> = mutableMapOf()
    private val skills: MutableMap<Uuid, SkillDto> = mutableMapOf()
    private val projects: MutableMap<Uuid, ProjectDetailDto> = mutableMapOf()
    private val writeups: MutableMap<Uuid, WriteupDetailDto> = mutableMapOf()
    private val documents: MutableMap<Uuid, DocumentDto> = mutableMapOf()
    private var profile: ProfileDto? = null

    fun modules(appEnv: String = "dev"): AdminAppModules {
        val config = AdminServerConfig(
            host = "127.0.0.1",
            port = 8081,
            appEnv = appEnv,
            devJwtSecret = AdminServerConfig.DEFAULT_DEV_JWT_SECRET,
            adminSessionSecret = null,
            allowedOwnerSubjects = setOf("dev-owner"),
            cfAccessTeamDomain = null,
            cfAccessAudience = null,
            oauthGithubClientId = null,
            oauthGoogleClientId = null,
        )

        val portfolioQueryRepo = object : PortfolioQueryRepository {
            override suspend fun getPortfolioOverview() = PortfolioOverviewDto(profile = profile)
            override suspend fun findProfile() = profile
            override suspend fun findAllExperiences() = emptyList<ExperienceDto>()
            override suspend fun findAllEducation() = emptyList<EducationDto>()
            override suspend fun findAllCertifications() = emptyList<CertificationDto>()
            override suspend fun findAllSkills() = skills.values.toList()
        }

        val portfolioCommandRepo = object : PortfolioCommandRepository {
            override suspend fun upsertProfile(request: UpdateProfileRequest): ProfileDto {
                val next = ProfileDto(
                    fullName = request.fullName,
                    headline = request.headline,
                    bioMarkdown = request.bioMarkdown,
                    location = request.location,
                    availability = request.availability,
                    socialLinks = request.socialLinks,
                    resumeDocumentId = request.resumeDocumentId,
                    updatedAt = fixedNow,
                )
                profile = next
                return next
            }

            override suspend fun createSkill(request: UpsertSkillRequest): SkillDto {
                val id = Uuid.random()
                val created = SkillDto(id.toString(), request.name, request.category, request.isFeatured, request.sortOrder)
                skills[id] = created
                return created
            }

            override suspend fun updateSkill(id: Uuid, request: UpsertSkillRequest): SkillDto? =
                skills[id]?.let {
                    val updated = it.copy(
                        name = request.name,
                        category = request.category,
                        isFeatured = request.isFeatured,
                        sortOrder = request.sortOrder,
                    )
                    skills[id] = updated
                    updated
                }

            override suspend fun deleteSkill(id: Uuid): Boolean = skills.remove(id) != null
            override suspend fun createExperience(request: UpsertExperienceRequest) = error("unused")
            override suspend fun updateExperience(id: Uuid, request: UpsertExperienceRequest): ExperienceDto? = null
            override suspend fun deleteExperience(id: Uuid) = false
            override suspend fun createEducation(request: UpsertEducationRequest) = error("unused")
            override suspend fun updateEducation(id: Uuid, request: UpsertEducationRequest): EducationDto? = null
            override suspend fun deleteEducation(id: Uuid) = false
            override suspend fun createCertification(request: UpsertCertificationRequest) = error("unused")
            override suspend fun updateCertification(id: Uuid, request: UpsertCertificationRequest): CertificationDto? = null
            override suspend fun deleteCertification(id: Uuid) = false
        }

        val projectQueryRepo = object : ProjectQueryRepository {
            override suspend fun getProjects(options: QueryOptions, featuredOnly: Boolean?, skillName: String?) =
                PagedResult.of(
                    items = projects.values.map { p ->
                        ProjectSummaryDto(
                            id = p.id,
                            slug = p.slug,
                            title = p.title,
                            summary = p.summary,
                            status = p.status,
                            isFeatured = p.isFeatured,
                            sortOrder = p.sortOrder,
                            updatedAt = p.updatedAt,
                        )
                    },
                    totalCount = projects.size.toLong(),
                    options = options,
                )

            override suspend fun findProjectBySlug(slug: String) = projects.values.firstOrNull { it.slug == slug }
            override suspend fun findProjectById(id: Uuid) = projects[id]
        }

        val projectCommandRepo = object : ProjectCommandRepository {
            override suspend fun createProject(request: UpsertProjectRequest): ProjectDetailDto {
                if (projects.values.any { it.slug == request.slug }) {
                    throw ConflictException("Duplicate project slug '${request.slug}'.", constraint = "projects_slug_key")
                }
                val id = Uuid.random()
                val created = ProjectDetailDto(
                    id = id.toString(),
                    slug = request.slug,
                    title = request.title,
                    summary = request.summary,
                    caseStudyMarkdown = request.caseStudyMarkdown,
                    repoUrl = request.repoUrl,
                    liveUrl = request.liveUrl,
                    coverDocumentId = request.coverDocumentId,
                    status = request.status,
                    isFeatured = request.isFeatured,
                    sortOrder = request.sortOrder,
                    createdAt = fixedNow,
                    updatedAt = fixedNow,
                )
                projects[id] = created
                return created
            }

            override suspend fun updateProject(id: Uuid, request: UpsertProjectRequest): ProjectDetailDto? = null
            override suspend fun deleteProject(id: Uuid): Boolean = projects.remove(id) != null
        }

        val writeupQueryRepo = object : WriteupQueryRepository {
            override suspend fun getWriteups(
                options: QueryOptions,
                publishedOnly: Boolean,
                tag: String?,
                searchQuery: String?,
            ): PagedResult<WriteupSummaryDto> {
                val items = writeups.values.map { w ->
                    WriteupSummaryDto(
                        id = w.id,
                        slug = w.slug,
                        title = w.title,
                        subtitle = w.subtitle,
                        summary = w.summary,
                        tags = w.tags,
                        status = w.status,
                        readingTimeMin = w.readingTimeMin,
                        publishedAt = w.publishedAt,
                        updatedAt = w.updatedAt,
                    )
                }
                return PagedResult.of(items, items.size.toLong(), options)
            }

            override suspend fun findWriteupBySlug(slug: String, publishedOnly: Boolean) = null
            override suspend fun findWriteupById(id: Uuid) = writeups[id]
        }

        val writeupCommandRepo = object : WriteupCommandRepository {
            override suspend fun createWriteup(request: UpsertWriteupRequest): WriteupDetailDto {
                val id = Uuid.random()
                val created = WriteupDetailDto(
                    id = id.toString(),
                    slug = request.slug,
                    title = request.title,
                    subtitle = request.subtitle,
                    summary = request.summary,
                    bodyMarkdown = request.bodyMarkdown,
                    tags = request.tags,
                    status = request.status,
                    readingTimeMin = request.readingTimeMin,
                    publishedAt = if (request.status == WriteupStatus.PUBLISHED) fixedNow else null,
                    createdAt = fixedNow,
                    updatedAt = fixedNow,
                )
                writeups[id] = created
                return created
            }

            override suspend fun updateWriteup(id: Uuid, request: UpsertWriteupRequest): WriteupDetailDto? = null
            override suspend fun deleteWriteup(id: Uuid): Boolean = writeups.remove(id) != null
        }

        val documentQueryRepo = object : DocumentQueryRepository {
            override suspend fun getDocuments(options: QueryOptions, publicOnly: Boolean) =
                PagedResult.of(documents.values.toList(), documents.size.toLong(), options)

            override suspend fun findDocumentById(id: Uuid, publicOnly: Boolean) = documents[id]
        }

        val documentCommandRepo = object : DocumentCommandRepository {
            override suspend fun saveConfirmedDocument(
                id: Uuid,
                sizeBytes: Long,
                mimeType: String,
                request: ConfirmUploadRequest,
            ): DocumentDto {
                val doc = DocumentDto(
                    id = id.toString(),
                    title = request.title,
                    category = request.category,
                    originalFilename = request.originalFilename,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    sha256 = request.sha256,
                    storageKey = request.storageKey,
                    isPublic = request.isPublic,
                    createdAt = fixedNow,
                )
                documents[id] = doc
                return doc
            }

            override suspend fun deleteDocument(id: Uuid): DocumentDto? = documents.remove(id)
        }

        val blobStorage = object : BlobStorage {
            override suspend fun presignPut(storageKey: String, contentType: String, ttl: Duration) =
                PresignedPutObject(
                    url = "http://127.0.0.1:9000/portfolio-assets/$storageKey",
                    requiredHeaders = mapOf("Content-Type" to contentType),
                    expiresAt = fixedNow,
                )

            override suspend fun verifyObject(storageKey: String): StoredObjectHead? =
                storedObjects[storageKey]?.let { bytes ->
                    StoredObjectHead(sizeBytes = bytes.size.toLong(), contentType = "application/pdf", leadingBytes = bytes)
                }

            override suspend fun presignGet(
                storageKey: String,
                downloadFilename: String,
                contentType: String,
                ttl: Duration,
            ) = "http://127.0.0.1:9000/portfolio-assets/$storageKey"

            override suspend fun deleteObject(storageKey: String) {
                storedObjects.remove(storageKey)
            }

            override fun close() = Unit
        }

        val analyticsQueryRepo = object : AnalyticsQueryRepository {
            override suspend fun getStatsOverview(range: StatsTimeRange) = StatsOverviewDto(
                range = range,
                totalEvents = 12L,
                uniqueVisitors = 4L,
                pageViews = 6L,
                writeupReads = 3L,
                projectClicks = 2L,
                documentDownloads = 1L,
            )
        }

        return AdminAppModules(
            config = config,
            authenticator = DefaultAdminAuthenticator(config),
            readinessChecker = ReadinessChecker { readiness.get() },
            queryService = AdminPortfolioQueryService(
                portfolioRepo = portfolioQueryRepo,
                projectRepo = projectQueryRepo,
                writeupRepo = writeupQueryRepo,
                documentRepo = documentQueryRepo,
            ),
            commandService = AdminPortfolioCommandService(
                portfolioRepo = portfolioCommandRepo,
                projectRepo = projectCommandRepo,
                writeupRepo = writeupCommandRepo,
            ),
            documentService = AdminDocumentCommandService(
                documentRepo = documentCommandRepo,
                blobStorage = blobStorage,
            ),
            statsService = AdminStatsQueryService(analyticsQueryRepo),
        )
    }
}
