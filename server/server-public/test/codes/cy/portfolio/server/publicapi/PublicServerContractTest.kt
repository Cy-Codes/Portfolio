package codes.cy.portfolio.server.publicapi

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.SocialLinkDto
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.serialization.PortfolioJson
import codes.cy.portfolio.server.db.config.SchemaReadinessStatus
import codes.cy.portfolio.server.db.repositories.AnalyticsCommandRepository
import codes.cy.portfolio.server.db.repositories.DocumentQueryRepository
import codes.cy.portfolio.server.db.repositories.PortfolioQueryRepository
import codes.cy.portfolio.server.db.repositories.ProjectQueryRepository
import codes.cy.portfolio.server.db.repositories.WriteupQueryRepository
import codes.cy.portfolio.server.db.storage.BlobStorageReader
import codes.cy.portfolio.server.publicapi.application.PublicAnalyticsCommandService
import codes.cy.portfolio.server.publicapi.application.PublicAppModules
import codes.cy.portfolio.server.publicapi.application.PublicPortfolioQueryService
import codes.cy.portfolio.server.publicapi.application.ReadinessChecker
import codes.cy.portfolio.server.publicapi.domain.PublicServerConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant as JavaInstant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PublicServerContractTest {

    @Test
    fun `health and readiness endpoints reflect database state`() = testApplication {
        val harness = TestPublicHarness()
        application { publicModule(harness.modules()) }
        val jsonClient = createClient { install(ContentNegotiation) { json(PortfolioJson) } }

        val liveResponse = jsonClient.get("/health")
        assertEquals(HttpStatusCode.OK, liveResponse.status)
        assertTrue(liveResponse.bodyAsText().contains("\"UP\""))

        val readyResponse = jsonClient.get("/health/ready")
        assertEquals(HttpStatusCode.OK, readyResponse.status)
        assertTrue(readyResponse.bodyAsText().contains("\"READY\""))

        harness.readiness.set(SchemaReadinessStatus(ready = false, appliedMigrationCount = 0))
        val notReadyResponse = jsonClient.get("/health/ready")
        assertEquals(HttpStatusCode.ServiceUnavailable, notReadyResponse.status)
        assertTrue(notReadyResponse.bodyAsText().contains("\"NOT_READY\""))
    }

    @Test
    fun `public portfolio, projects, writeups, and CWE-190 validation obey read contracts`() = testApplication {
        val harness = TestPublicHarness()
        application { publicModule(harness.modules()) }
        val jsonClient = createClient { install(ContentNegotiation) { json(PortfolioJson) } }

        val portfolioResp = jsonClient.get("/api/v1/public/portfolio")
        assertEquals(HttpStatusCode.OK, portfolioResp.status)
        assertEquals("CY", portfolioResp.body<PortfolioOverviewDto>().profile?.fullName)

        val projectsResp = jsonClient.get("/api/v1/public/projects?featured=true")
        assertEquals(HttpStatusCode.OK, projectsResp.status)
        assertEquals(1, projectsResp.body<PagedResult<ProjectSummaryDto>>().items.size)

        val projectDetailResp = jsonClient.get("/api/v1/public/projects/kmp-portfolio")
        assertEquals(HttpStatusCode.OK, projectDetailResp.status)
        assertEquals("kmp-portfolio", projectDetailResp.body<ProjectDetailDto>().slug)

        val badSlugResp = jsonClient.get("/api/v1/public/projects/BAD_SLUG!")
        assertEquals(HttpStatusCode.BadRequest, badSlugResp.status)
        assertIs<ApiError.ValidationError>(PortfolioJson.decodeFromString<ApiError>(badSlugResp.bodyAsText()))

        val overflowResp = jsonClient.get("/api/v1/public/projects?page=21474838&pageSize=100")
        assertEquals(HttpStatusCode.BadRequest, overflowResp.status)
        assertIs<ApiError.ValidationError>(PortfolioJson.decodeFromString<ApiError>(overflowResp.bodyAsText()))

        val badSortResp = jsonClient.get("/api/v1/public/projects?sortBy=drop_table")
        assertEquals(HttpStatusCode.BadRequest, badSortResp.status)

        val writeupsResp = jsonClient.get("/api/v1/public/writeups")
        assertEquals(HttpStatusCode.OK, writeupsResp.status)
        val writeups = writeupsResp.body<PagedResult<WriteupSummaryDto>>()
        assertEquals(1, writeups.items.size)
        assertEquals("published-writeup", writeups.items.single().slug)

        val draftResp = jsonClient.get("/api/v1/public/writeups/secret-draft")
        assertEquals(HttpStatusCode.NotFound, draftResp.status)
        assertIs<ApiError.NotFound>(PortfolioJson.decodeFromString<ApiError>(draftResp.bodyAsText()))
    }

    @Test
    fun `document download stays read-only and events endpoint records cookieless analytics`() = testApplication {
        val harness = TestPublicHarness()
        application { publicModule(harness.modules()) }
        val noRedirectClient = createClient { followRedirects = false }
        val jsonClient = createClient { install(ContentNegotiation) { json(PortfolioJson) } }

        val docsResp = jsonClient.get("/api/v1/public/documents")
        assertEquals(HttpStatusCode.OK, docsResp.status)
        val docsPage = docsResp.body<PagedResult<DocumentDto>>()
        assertEquals(1, docsPage.items.size)
        assertTrue(docsPage.items.all { it.isPublic })

        val docsOverflowResp = jsonClient.get("/api/v1/public/documents?page=21474838&pageSize=100")
        assertEquals(HttpStatusCode.BadRequest, docsOverflowResp.status)

        val downloadResp = noRedirectClient.get("/api/v1/public/documents/${harness.publicDocId}/download")
        assertEquals(HttpStatusCode.Found, downloadResp.status)
        assertEquals("nosniff", downloadResp.headers["X-Content-Type-Options"])
        assertEquals("https://r2.example.test/signed/resume.pdf", downloadResp.headers[HttpHeaders.Location])
        assertTrue(harness.recordedEvents.isEmpty())

        val privateDocResp = noRedirectClient.get("/api/v1/public/documents/${harness.privateDocId}/download")
        assertEquals(HttpStatusCode.NotFound, privateDocResp.status)

        val badDocIdResp = noRedirectClient.get("/api/v1/public/documents/not-a-uuid/download")
        assertEquals(HttpStatusCode.BadRequest, badDocIdResp.status)

        val eventResp = jsonClient.post("/api/v1/public/events") {
            contentType(ContentType.Application.Json)
            setBody(
                TrackEventRequest(
                    eventType = AnalyticsEventType.DOCUMENT_DOWNLOAD,
                    path = "/resume",
                    entityId = harness.publicDocId.toString(),
                    referrerHost = "GitHub.com",
                    platform = ClientPlatform.WEB,
                    viewportTier = ViewportTier.LARGE,
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, eventResp.status)
        assertEquals(1, harness.recordedEvents.size)
        assertEquals(64, harness.recordedEvents.single().second.length)
        assertEquals("github.com", harness.recordedEvents.single().first.referrerHost)

        val badEventResp = jsonClient.post("/api/v1/public/events") {
            contentType(ContentType.Application.Json)
            setBody(
                TrackEventRequest(
                    eventType = AnalyticsEventType.PAGE_VIEW,
                    path = "relative-path-without-slash",
                    platform = ClientPlatform.IOS,
                    viewportTier = ViewportTier.COMPACT,
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, badEventResp.status)
    }

    @Test
    fun `reader view, sitemap, robots, dynamic SEO shell, and prod 500 sanitization behave as specified`() = testApplication {
        val harness = TestPublicHarness()
        application { publicModule(harness.modules(appEnv = "prod")) }

        val readResp = client.get("/read/published-writeup")
        assertEquals(HttpStatusCode.OK, readResp.status)
        val readHtml = readResp.bodyAsText()
        assertTrue(readHtml.contains("rel=\"canonical\""))
        assertTrue(readHtml.contains("href=\"https://cy.codes/read/published-writeup\""))
        assertTrue(readHtml.contains("application/ld+json"))
        assertTrue(readHtml.contains("Interactive Demo: <code>adaptive-breakpoints</code>"))
        assertFalse(readHtml.contains("<script>alert(1)</script>"))
        assertTrue(readHtml.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))

        val sitemapResp = client.get("/sitemap.xml")
        assertEquals(HttpStatusCode.OK, sitemapResp.status)
        val sitemap = sitemapResp.bodyAsText()
        assertTrue(sitemap.contains("https://cy.codes/projects/kmp-portfolio"))
        assertTrue(sitemap.contains("https://cy.codes/read/published-writeup"))
        assertFalse(sitemap.contains("secret-draft"))

        val robotsResp = client.get("/robots.txt")
        assertEquals(HttpStatusCode.OK, robotsResp.status)
        assertTrue(robotsResp.bodyAsText().contains("Disallow: /api/v1/admin/"))

        val homeResp = client.get("/")
        assertEquals(HttpStatusCode.OK, homeResp.status)
        assertTrue(homeResp.bodyAsText().contains("ProfilePage"))

        val missingApiResp = client.get("/api/v1/public/unknown-endpoint")
        assertEquals(HttpStatusCode.NotFound, missingApiResp.status)
        assertIs<ApiError.NotFound>(PortfolioJson.decodeFromString<ApiError>(missingApiResp.bodyAsText()))

        harness.throwOnPortfolio.set(IllegalStateException("jdbc://db-password-secret"))
        val internalErrResp = client.get("/api/v1/public/portfolio")
        assertEquals(HttpStatusCode.InternalServerError, internalErrResp.status)
        val decodedErr = assertIs<ApiError.InternalError>(
            PortfolioJson.decodeFromString<ApiError>(internalErrResp.bodyAsText()),
        )
        assertEquals(ApiError.GENERIC_INTERNAL_MESSAGE, decodedErr.message)
        assertNotNull(decodedErr.traceId)
        assertFalse(internalErrResp.bodyAsText().contains("db-password-secret"))
    }

    @Test
    fun `wasm SEO shell emits import-map-loader and module webApp mjs script when static dir provides them`() = testApplication {
        val tempDir = kotlin.io.path.createTempDirectory("portfolio-wasm-static")
        try {
            java.nio.file.Files.writeString(tempDir.resolve("import-map-loader.js"), "// import map")
            java.nio.file.Files.writeString(tempDir.resolve("webApp.mjs"), "export default {};")
            java.nio.file.Files.writeString(tempDir.resolve("styles.css"), "html, body { margin: 0; }")

            val harness = TestPublicHarness()
            application { publicModule(harness.modules(webStaticDir = tempDir.toAbsolutePath().toString())) }

            val response = client.get("/writeups/published-writeup")
            assertEquals(HttpStatusCode.OK, response.status)
            val html = response.bodyAsText()
            assertTrue(html.contains("<link href=\"/styles.css\" rel=\"stylesheet\">"))
            assertTrue(html.contains("<script src=\"/import-map-loader.js\"></script>"))
            assertTrue(html.contains("<script src=\"/webApp.mjs\" type=\"module\"></script>"))
            assertTrue(html.contains("TechArticle"))
            assertTrue(html.contains("id=\"portfolio-root\""))
            assertTrue(html.contains("Compose &amp; SwiftUI Interop"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

private class TestPublicHarness {
    val fixedNow: Instant = Instant.parse("2026-11-20T12:00:00Z")
    val publicDocId: Uuid = Uuid.parse("10000000-0000-0000-0000-000000000001")
    val privateDocId: Uuid = Uuid.parse("10000000-0000-0000-0000-000000000009")
    val readiness: AtomicReference<SchemaReadinessStatus> = AtomicReference(
        SchemaReadinessStatus(ready = true, appliedMigrationCount = 2),
    )
    val throwOnPortfolio: AtomicReference<Throwable?> = AtomicReference(null)
    val recordedEvents: MutableList<Pair<TrackEventRequest, String>> = mutableListOf()

    private val sampleProject = ProjectDetailDto(
        id = "60000000-0000-0000-0000-000000000001",
        slug = "kmp-portfolio",
        title = "Adaptive KMP Portfolio",
        summary = "Full-stack KMP showcase",
        caseStudyMarkdown = "# Overview\nBuilt with KMP & Ktor.",
        status = ProjectStatus.ACTIVE,
        isFeatured = true,
        sortOrder = 1,
        createdAt = fixedNow,
        updatedAt = fixedNow,
    )

    private val publishedWriteup = WriteupDetailDto(
        id = "70000000-0000-0000-0000-000000000001",
        slug = "published-writeup",
        title = "Compose & SwiftUI Interop",
        subtitle = "Shared MVI state",
        summary = "Embedding interactive Compose demos inside SwiftUI and Ktor.",
        bodyMarkdown = "# Intro\n\n<script>alert(1)</script>\n\n```demo:adaptive-breakpoints\n```",
        tags = listOf("kotlin-multiplatform", "swiftui"),
        status = WriteupStatus.PUBLISHED,
        readingTimeMin = 5,
        publishedAt = fixedNow,
        createdAt = fixedNow,
        updatedAt = fixedNow,
    )

    private val draftWriteup = publishedWriteup.copy(
        id = "70000000-0000-0000-0000-000000000002",
        slug = "secret-draft",
        status = WriteupStatus.DRAFT,
        publishedAt = null,
    )

    private val publicDoc = DocumentDto(
        id = publicDocId.toString(),
        title = "Resume 2026",
        category = DocumentCategory.RESUME,
        originalFilename = "resume.pdf",
        mimeType = "application/pdf",
        sizeBytes = 1024L,
        sha256 = "a".repeat(64),
        storageKey = "documents/$publicDocId/resume.pdf",
        isPublic = true,
        downloadCount = 0L,
        createdAt = fixedNow,
    )

    private val privateDoc = publicDoc.copy(id = privateDocId.toString(), isPublic = false)

    fun modules(
        appEnv: String = "dev",
        webStaticDir: String? = null,
    ): PublicAppModules {
        val config = PublicServerConfig(
            host = "127.0.0.1",
            port = 8080,
            appEnv = appEnv,
            publicSiteBaseUrl = "https://cy.codes",
            analyticsSalt = "unit-test-salt",
            webStaticDir = webStaticDir,
        )
        val portfolioRepo = object : PortfolioQueryRepository {
            override suspend fun getPortfolioOverview(): PortfolioOverviewDto {
                throwOnPortfolio.get()?.let { throw it }
                return PortfolioOverviewDto(
                    profile = ProfileDto(
                        fullName = "CY",
                        headline = "Staff KMP & Backend Engineer",
                        bioMarkdown = "Engineering leader",
                        socialLinks = listOf(SocialLinkDto("GitHub", "https://github.com/cy")),
                        updatedAt = fixedNow,
                    ),
                    featuredSkills = listOf(SkillDto("1", "Kotlin", "Languages", isFeatured = true, sortOrder = 1)),
                )
            }

            override suspend fun findProfile() = getPortfolioOverview().profile
            override suspend fun findAllExperiences() = emptyList<codes.cy.portfolio.core.model.ExperienceDto>()
            override suspend fun findAllEducation() = emptyList<codes.cy.portfolio.core.model.EducationDto>()
            override suspend fun findAllCertifications() = emptyList<codes.cy.portfolio.core.model.CertificationDto>()
            override suspend fun findAllSkills() = emptyList<SkillDto>()
        }

        val projectRepo = object : ProjectQueryRepository {
            override suspend fun getProjects(
                options: QueryOptions,
                featuredOnly: Boolean?,
                skillName: String?,
            ): PagedResult<ProjectSummaryDto> {
                val summary = ProjectSummaryDto(
                    id = sampleProject.id,
                    slug = sampleProject.slug,
                    title = sampleProject.title,
                    summary = sampleProject.summary,
                    status = sampleProject.status,
                    isFeatured = sampleProject.isFeatured,
                    sortOrder = sampleProject.sortOrder,
                    updatedAt = sampleProject.updatedAt,
                )
                return PagedResult.of(listOf(summary), 1L, options)
            }

            override suspend fun findProjectBySlug(slug: String): ProjectDetailDto? =
                sampleProject.takeIf { it.slug == slug }

            override suspend fun findProjectById(id: Uuid): ProjectDetailDto? = null
        }

        val writeupRepo = object : WriteupQueryRepository {
            private val allWriteups = listOf(publishedWriteup, draftWriteup)

            override suspend fun getWriteups(
                options: QueryOptions,
                publishedOnly: Boolean,
                tag: String?,
                searchQuery: String?,
            ): PagedResult<WriteupSummaryDto> {
                val filtered = allWriteups
                    .filter { !publishedOnly || it.status == WriteupStatus.PUBLISHED }
                    .map { w ->
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
                return PagedResult.of(filtered, filtered.size.toLong(), options)
            }

            override suspend fun findWriteupBySlug(slug: String, publishedOnly: Boolean): WriteupDetailDto? =
                allWriteups.firstOrNull {
                    it.slug == slug && (!publishedOnly || it.status == WriteupStatus.PUBLISHED)
                }

            override suspend fun findWriteupById(id: Uuid): WriteupDetailDto? = null
        }

        val documentRepo = object : DocumentQueryRepository {
            private val docs = listOf(publicDoc, privateDoc)

            override suspend fun getDocuments(options: QueryOptions, publicOnly: Boolean): PagedResult<DocumentDto> {
                val filtered = docs.filter { !publicOnly || it.isPublic }
                return PagedResult.of(filtered, filtered.size.toLong(), options)
            }

            override suspend fun findDocumentById(id: Uuid, publicOnly: Boolean): DocumentDto? =
                docs.firstOrNull { it.id == id.toString() && (!publicOnly || it.isPublic) }
        }

        val blobReader = object : BlobStorageReader {
            override suspend fun presignGet(
                storageKey: String,
                downloadFilename: String,
                contentType: String,
                ttl: Duration,
            ): String = "https://r2.example.test/signed/$downloadFilename"

            override fun close() = Unit
        }

        val analyticsRepo = object : AnalyticsCommandRepository {
            override suspend fun recordEvent(request: TrackEventRequest, visitorHash: String) {
                recordedEvents.add(request to visitorHash)
            }
        }

        val fixedClock = Clock.fixed(JavaInstant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC)
        return PublicAppModules(
            config = config,
            readinessChecker = ReadinessChecker { readiness.get() },
            queryService = PublicPortfolioQueryService(
                portfolioRepo = portfolioRepo,
                projectRepo = projectRepo,
                writeupRepo = writeupRepo,
                documentRepo = documentRepo,
                blobStorageReader = blobReader,
                siteBaseUrl = config.publicSiteBaseUrl,
            ),
            analyticsService = PublicAnalyticsCommandService(
                analyticsRepo = analyticsRepo,
                analyticsSalt = config.analyticsSalt,
                clock = fixedClock,
            ),
        )
    }
}
