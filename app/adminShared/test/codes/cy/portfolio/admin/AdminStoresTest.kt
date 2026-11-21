package codes.cy.portfolio.admin

import codes.cy.portfolio.admin.crypto.sha256Hex
import codes.cy.portfolio.admin.data.AdminApiResult
import codes.cy.portfolio.admin.data.AdminAuthMode
import codes.cy.portfolio.admin.data.AdminCommandApi
import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.AdminSessionConfigHolder
import codes.cy.portfolio.admin.features.auth.AdminAuthAction
import codes.cy.portfolio.admin.features.auth.AdminAuthIntent
import codes.cy.portfolio.admin.features.auth.AdminAuthStoreFactory
import codes.cy.portfolio.admin.features.career.AdminCareerIntent
import codes.cy.portfolio.admin.features.career.AdminCareerStoreFactory
import codes.cy.portfolio.admin.features.documents.AdminDocumentsIntent
import codes.cy.portfolio.admin.features.documents.AdminDocumentsStoreFactory
import codes.cy.portfolio.admin.features.documents.UploadStage
import codes.cy.portfolio.admin.features.projects.AdminProjectsIntent
import codes.cy.portfolio.admin.features.projects.AdminProjectsStoreFactory
import codes.cy.portfolio.admin.features.stats.AdminStatsIntent
import codes.cy.portfolio.admin.features.stats.AdminStatsStoreFactory
import codes.cy.portfolio.admin.features.writeups.AdminWriteupsIntent
import codes.cy.portfolio.admin.features.writeups.AdminWriteupsStoreFactory
import codes.cy.portfolio.admin.navigation.AdminDeepLink
import codes.cy.portfolio.admin.navigation.AdminDependencies
import codes.cy.portfolio.admin.navigation.AdminRootComponent
import codes.cy.portfolio.admin.navigation.AdminSection
import codes.cy.portfolio.admin.navigation.DefaultAdminRootComponent
import codes.cy.portfolio.admin.platform.SelectedUploadFile
import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.AdminSessionDto
import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
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
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import pro.respawn.flowmvi.test.subscribeAndTest
import pro.respawn.flowmvi.test.wait

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalDecomposeApi::class)
class AdminStoresTest {

    @Test
    fun `AdminAuthStore verifies valid tokens updates configHolder and handles sign out`() = runTest {
        val holder = AdminSessionConfigHolder("http://127.0.0.1:8081")
        val fake = FakeAdminApi(holder)
        val store = AdminAuthStoreFactory.create(
            queryApi = fake,
            configHolder = holder,
            initialMode = AdminAuthMode.DEV_BEARER_TOKEN,
            initialToken = "",
        )

        store.subscribeAndTest {
            emit(AdminAuthIntent.TokenChanged("bad-token"))
            emit(AdminAuthIntent.SignIn)
            wait()
            assertFalse(states.value.isAuthenticated)
            assertTrue(states.value.errorMessage?.contains("Invalid dev JWT") == true)
            assertFalse(holder.credentials.hasToken)

            emit(AdminAuthIntent.TokenChanged("valid-owner-jwt"))
            emit(AdminAuthIntent.SignIn)
            wait()
            assertTrue(states.value.isAuthenticated)
            assertEquals("dev-owner", states.value.session?.subject)
            assertEquals("valid-owner-jwt", holder.credentials.sanitizedToken)
            val action = withTimeout(1000L) { actions.first() }
            assertIs<AdminAuthAction.Authenticated>(action)

            emit(AdminAuthIntent.SignOut)
            wait()
            assertFalse(states.value.isAuthenticated)
            assertFalse(holder.credentials.hasToken)
            assertIs<AdminAuthAction.SignedOut>(withTimeout(1000L) { actions.first() })
        }
    }

    @Test
    fun `AdminStatsStore loads overview and switches range filters cleanly`() = runTest {
        val fake = FakeAdminApi()
        val store = AdminStatsStoreFactory.create(fake)

        store.subscribeAndTest {
            wait()
            assertEquals(StatsTimeRange.THIRTY_DAYS, states.value.selectedRange)
            assertEquals(200L, states.value.overview?.totalEvents)

            emit(AdminStatsIntent.SelectRange(StatsTimeRange.SEVEN_DAYS))
            wait()
            assertEquals(StatsTimeRange.SEVEN_DAYS, states.value.selectedRange)
            assertEquals(50L, states.value.overview?.totalEvents)
        }
    }

    @Test
    fun `AdminCareerStore loads career data updates profile and creates skill`() = runTest {
        val fake = FakeAdminApi()
        val store = AdminCareerStoreFactory.create(fake, fake)

        store.subscribeAndTest {
            wait()
            assertEquals("CY", states.value.profileDraft.fullName)
            assertEquals(1, states.value.skills.size)

            emit(AdminCareerIntent.UpdateProfileDraft(states.value.profileDraft.copy(headline = "Principal KMP Architect")))
            emit(AdminCareerIntent.SaveProfile)
            wait()
            assertEquals("Principal KMP Architect", fake.savedProfile.headline)

            emit(
                AdminCareerIntent.UpdateSkillDraft(
                    states.value.skillDraft.copy(name = "Ktor", category = "Backend", isFeatured = true),
                ),
            )
            emit(AdminCareerIntent.SaveSkill)
            wait()
            assertEquals(2, states.value.skills.size)
            assertTrue(states.value.skills.any { it.name == "Ktor" })
        }
    }

    @Test
    fun `AdminWriteupsStore inserts demo fence and supports Draft Publish Archive lifecycle`() = runTest {
        val fake = FakeAdminApi()
        val store = AdminWriteupsStoreFactory.create(fake, fake)

        store.subscribeAndTest {
            wait()
            assertEquals(1, states.value.items.size)

            emit(
                AdminWriteupsIntent.UpdateDraft(
                    states.value.draft.copy(
                        title = "Phase 6 Live Preview",
                        summary = "Demonstrates Markdown + DemoRegistry inside admin editor",
                        bodyMarkdown = "# Section One",
                    ),
                ),
            )
            emit(AdminWriteupsIntent.AutoSlugFromTitle)
            emit(AdminWriteupsIntent.InsertDemoSnippet("adaptive-breakpoints"))
            emit(AdminWriteupsIntent.SaveWithStatus(WriteupStatus.PUBLISHED))
            wait()

            assertEquals("phase-6-live-preview", states.value.draft.slug)
            assertEquals(WriteupStatus.PUBLISHED, states.value.draft.status)
            assertTrue(states.value.draft.bodyMarkdown.contains("```demo:adaptive-breakpoints\n```"))
            assertEquals(2, states.value.items.size)

            emit(AdminWriteupsIntent.SaveWithStatus(WriteupStatus.ARCHIVED))
            wait()
            assertEquals(WriteupStatus.ARCHIVED, states.value.draft.status)
        }
    }

    @Test
    fun `AdminProjectsStore creates case study and AdminDocumentsStore completes R2 upload flow`() = runTest {
        val fake = FakeAdminApi()
        val projectStore = AdminProjectsStoreFactory.create(fake, fake)

        projectStore.subscribeAndTest {
            wait()
            emit(
                AdminProjectsIntent.UpdateDraft(
                    states.value.draft.copy(
                        title = "Adaptive Kit",
                        slug = "adaptive-kit",
                        summary = "5-tier Material 3 layout engine",
                        caseStudyMarkdown = "# Adaptive Kit",
                        status = ProjectStatus.ACTIVE,
                        isFeatured = true,
                    ),
                ),
            )
            emit(AdminProjectsIntent.SaveProject)
            wait()
            assertEquals("adaptive-kit", states.value.draft.slug)
            assertEquals(2, states.value.items.size)
        }

        val samplePdf = "%PDF-1.7\nPortfolio Resume".encodeToByteArray()
        val uploadFile = SelectedUploadFile(
            filename = "cv-2026.pdf",
            mimeType = "application/pdf",
            bytes = samplePdf,
        )
        val docsStore = AdminDocumentsStoreFactory.create(
            queryApi = fake,
            commandApi = fake,
            filePicker = { uploadFile },
            hashContext = EmptyCoroutineContext,
        )

        docsStore.subscribeAndTest {
            wait()
            emit(AdminDocumentsIntent.PickFile)
            wait()
            assertEquals(uploadFile, states.value.selectedFile)
            assertEquals(samplePdf.sha256Hex(), states.value.computedSha256)
            assertEquals("cv-2026", states.value.titleInput)

            emit(AdminDocumentsIntent.CategoryChanged(DocumentCategory.RESUME))
            emit(AdminDocumentsIntent.UploadSelectedFile)
            wait()

            assertEquals(UploadStage.IDLE, states.value.uploadStage)
            assertNull(states.value.selectedFile)
            assertTrue(fake.uploadedPutUrls.single().startsWith("http://127.0.0.1:9000/"))
            assertTrue(fake.uploadedBytes.single().contentEquals(samplePdf))
            assertEquals(2, states.value.items.size)
        }
    }

    @Test
    fun `DefaultAdminRootComponent gates workspace behind auth and navigates sections via deep links`() = runTest {
        val fake = FakeAdminApi()
        val lifecycle = LifecycleRegistry()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dependencies = AdminDependencies(
            configHolder = AdminSessionConfigHolder("http://127.0.0.1:8081"),
            queryApi = fake,
            commandApi = fake,
            initialToken = "",
            autoVerifyInitialToken = false,
            mainContext = dispatcher,
            ioContext = dispatcher,
        )

        val root = DefaultAdminRootComponent.fromDeepLinkPath(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            dependencies = dependencies,
            initialPath = "/admin/writeups",
        )
        lifecycle.resume()

        assertIs<AdminRootComponent.Child.SignIn>(root.childStack.value.active.instance)
        assertFalse(root.authState.value.isAuthenticated)
        assertEquals(0, fake.statsCallCount)

        root.onAuthIntent(AdminAuthIntent.TokenChanged("valid-owner-jwt"))
        root.onAuthIntent(AdminAuthIntent.SignIn)
        testScheduler.advanceUntilIdle()

        assertIs<AdminRootComponent.Child.Writeups>(root.childStack.value.active.instance)
        assertEquals(AdminSection.WRITEUPS, root.activeSection.value)

        root.selectSection(AdminSection.STATS)
        testScheduler.advanceUntilIdle()
        assertIs<AdminRootComponent.Child.Stats>(root.childStack.value.active.instance)
        assertEquals(AdminSection.STATS, root.activeSection.value)

        root.signOut()
        testScheduler.advanceUntilIdle()
        assertIs<AdminRootComponent.Child.SignIn>(root.childStack.value.active.instance)
        assertEquals(AdminSection.DOCUMENTS, AdminDeepLink.sectionFromPath("/admin/documents"))

        lifecycle.destroy()
    }
}

private class FakeAdminApi(
    private val holder: AdminSessionConfigHolder? = null,
) : AdminQueryApi, AdminCommandApi {
    private val fixedNow = Instant.parse("2026-11-21T00:00:00Z")
    var statsCallCount: Int = 0
    val uploadedPutUrls: MutableList<String> = mutableListOf()
    val uploadedBytes: MutableList<ByteArray> = mutableListOf()
    var savedProfile: ProfileDto = ProfileDto(
        fullName = "CY",
        headline = "Systems Engineer",
        bioMarkdown = "# Bio",
        updatedAt = fixedNow,
    )

    private val skills = mutableListOf(
        SkillDto(id = "skill-1", name = "Kotlin", category = "Languages", isFeatured = true, sortOrder = 1),
    )
    private val experiences = mutableListOf<ExperienceDto>()
    private val education = mutableListOf<EducationDto>()
    private val certifications = mutableListOf<CertificationDto>()
    private val projects = mutableListOf(
        ProjectDetailDto(
            id = "proj-1",
            slug = "portfolio-kmp",
            title = "Portfolio KMP",
            summary = "Existing project",
            caseStudyMarkdown = "# Portfolio",
            status = ProjectStatus.ACTIVE,
            isFeatured = true,
            sortOrder = 1,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        ),
    )
    private val writeups = mutableListOf(
        WriteupDetailDto(
            id = "w-1",
            slug = "existing-note",
            title = "Existing Note",
            summary = "Seeded summary",
            bodyMarkdown = "# Hello",
            status = WriteupStatus.DRAFT,
            readingTimeMin = 2,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        ),
    )
    private val documents = mutableListOf(
        DocumentDto(
            id = "doc-1",
            title = "Existing Asset",
            category = DocumentCategory.ATTACHMENT,
            originalFilename = "diagram.png",
            mimeType = "image/png",
            sizeBytes = 512L,
            sha256 = "a".repeat(64),
            storageKey = "documents/doc-1/diagram.png",
            isPublic = true,
            createdAt = fixedNow,
        ),
    )

    override suspend fun getSession(): AdminApiResult<AdminSessionDto> {
        val activeToken = holder?.credentials?.sanitizedToken.orEmpty()
        return if (activeToken == "bad-token") {
            AdminApiResult.Failure(ApiError.Unauthorized("Invalid dev JWT"))
        } else {
            AdminApiResult.Success(AdminSessionDto(subject = "dev-owner", role = "owner", authSource = "dev-jwt"))
        }
    }

    override suspend fun getStatsOverview(range: StatsTimeRange): AdminApiResult<StatsOverviewDto> {
        statsCallCount += 1
        val events = if (range == StatsTimeRange.SEVEN_DAYS) 50L else 200L
        return AdminApiResult.Success(
            StatsOverviewDto(
                range = range,
                totalEvents = events,
                uniqueVisitors = events / 2L,
                pageViews = events / 2L,
                writeupReads = 15L,
                projectClicks = 10L,
                documentDownloads = 5L,
            ),
        )
    }

    override suspend fun getProfile(): AdminApiResult<ProfileDto> = AdminApiResult.Success(savedProfile)

    override suspend fun updateProfile(request: UpdateProfileRequest): AdminApiResult<ProfileDto> {
        savedProfile = savedProfile.copy(
            fullName = request.fullName,
            headline = request.headline,
            bioMarkdown = request.bioMarkdown,
            location = request.location,
            availability = request.availability,
            socialLinks = request.socialLinks,
            resumeDocumentId = request.resumeDocumentId,
            updatedAt = fixedNow,
        )
        return AdminApiResult.Success(savedProfile)
    }

    override suspend fun getSkills(): AdminApiResult<List<SkillDto>> = AdminApiResult.Success(skills.toList())

    override suspend fun createSkill(request: UpsertSkillRequest): AdminApiResult<SkillDto> {
        val dto = SkillDto(
            id = "skill-${skills.size + 1}",
            name = request.name,
            category = request.category,
            isFeatured = request.isFeatured,
            sortOrder = request.sortOrder,
        )
        skills.add(dto)
        return AdminApiResult.Success(dto)
    }

    override suspend fun updateSkill(id: String, request: UpsertSkillRequest): AdminApiResult<SkillDto> =
        AdminApiResult.Failure(ApiError.NotFound("Not found", "skill", id))

    override suspend fun deleteSkill(id: String): AdminApiResult<Unit> = AdminApiResult.Success(Unit)

    override suspend fun getExperiences(): AdminApiResult<List<ExperienceDto>> =
        AdminApiResult.Success(experiences.toList())

    override suspend fun createExperience(request: UpsertExperienceRequest): AdminApiResult<ExperienceDto> {
        val dto = ExperienceDto(
            id = "exp-${experiences.size + 1}",
            company = request.company,
            title = request.title,
            location = request.location,
            startDate = LocalDate.parse(request.startDate.toString()),
            endDate = request.endDate,
            isCurrent = request.isCurrent,
            highlights = request.highlights,
            sortOrder = request.sortOrder,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        )
        experiences.add(dto)
        return AdminApiResult.Success(dto)
    }

    override suspend fun updateExperience(id: String, request: UpsertExperienceRequest): AdminApiResult<ExperienceDto> =
        AdminApiResult.Failure(ApiError.NotFound("Not found", "experience", id))

    override suspend fun deleteExperience(id: String): AdminApiResult<Unit> = AdminApiResult.Success(Unit)

    override suspend fun getEducation(): AdminApiResult<List<EducationDto>> =
        AdminApiResult.Success(education.toList())

    override suspend fun createEducation(request: UpsertEducationRequest): AdminApiResult<EducationDto> =
        AdminApiResult.Failure(ApiError.InternalError("unused"))

    override suspend fun updateEducation(id: String, request: UpsertEducationRequest): AdminApiResult<EducationDto> =
        AdminApiResult.Failure(ApiError.InternalError("unused"))

    override suspend fun deleteEducation(id: String): AdminApiResult<Unit> = AdminApiResult.Success(Unit)

    override suspend fun getCertifications(): AdminApiResult<List<CertificationDto>> =
        AdminApiResult.Success(certifications.toList())

    override suspend fun createCertification(request: UpsertCertificationRequest): AdminApiResult<CertificationDto> =
        AdminApiResult.Failure(ApiError.InternalError("unused"))

    override suspend fun updateCertification(
        id: String,
        request: UpsertCertificationRequest,
    ): AdminApiResult<CertificationDto> = AdminApiResult.Failure(ApiError.InternalError("unused"))

    override suspend fun deleteCertification(id: String): AdminApiResult<Unit> = AdminApiResult.Success(Unit)

    override suspend fun getProjects(options: QueryOptions): AdminApiResult<PagedResult<ProjectSummaryDto>> =
        AdminApiResult.Success(
            PagedResult.of(
                items = projects.map {
                    ProjectSummaryDto(
                        id = it.id,
                        slug = it.slug,
                        title = it.title,
                        summary = it.summary,
                        repoUrl = it.repoUrl,
                        liveUrl = it.liveUrl,
                        coverDocumentId = it.coverDocumentId,
                        status = it.status,
                        isFeatured = it.isFeatured,
                        skills = it.skills,
                        sortOrder = it.sortOrder,
                        updatedAt = it.updatedAt,
                    )
                },
                totalCount = projects.size.toLong(),
                options = options,
            ),
        )

    override suspend fun getProjectById(id: String): AdminApiResult<ProjectDetailDto> =
        projects.firstOrNull { it.id == id }?.let { AdminApiResult.Success(it) }
            ?: AdminApiResult.Failure(ApiError.NotFound("Missing", "project", id))

    override suspend fun createProject(request: UpsertProjectRequest): AdminApiResult<ProjectDetailDto> {
        val dto = ProjectDetailDto(
            id = "proj-${projects.size + 1}",
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
        projects.add(dto)
        return AdminApiResult.Success(dto)
    }

    override suspend fun updateProject(id: String, request: UpsertProjectRequest): AdminApiResult<ProjectDetailDto> =
        createProject(request)

    override suspend fun deleteProject(id: String): AdminApiResult<Unit> = AdminApiResult.Success(Unit)

    override suspend fun getWriteups(options: QueryOptions): AdminApiResult<PagedResult<WriteupSummaryDto>> =
        AdminApiResult.Success(
            PagedResult.of(
                items = writeups.map {
                    WriteupSummaryDto(
                        id = it.id,
                        slug = it.slug,
                        title = it.title,
                        subtitle = it.subtitle,
                        summary = it.summary,
                        tags = it.tags,
                        status = it.status,
                        readingTimeMin = it.readingTimeMin,
                        coverDocumentId = it.coverDocumentId,
                        publishedAt = it.publishedAt,
                        updatedAt = it.updatedAt,
                    )
                },
                totalCount = writeups.size.toLong(),
                options = options,
            ),
        )

    override suspend fun getWriteupById(id: String): AdminApiResult<WriteupDetailDto> =
        writeups.firstOrNull { it.id == id }?.let { AdminApiResult.Success(it) }
            ?: AdminApiResult.Failure(ApiError.NotFound("Missing", "writeup", id))

    override suspend fun createWriteup(request: UpsertWriteupRequest): AdminApiResult<WriteupDetailDto> {
        val dto = WriteupDetailDto(
            id = "w-${writeups.size + 1}",
            slug = request.slug,
            title = request.title,
            subtitle = request.subtitle,
            summary = request.summary,
            bodyMarkdown = request.bodyMarkdown,
            tags = request.tags,
            status = request.status,
            readingTimeMin = request.readingTimeMin,
            coverDocumentId = request.coverDocumentId,
            publishedAt = if (request.status == WriteupStatus.PUBLISHED) fixedNow else null,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        )
        writeups.add(dto)
        return AdminApiResult.Success(dto)
    }

    override suspend fun updateWriteup(id: String, request: UpsertWriteupRequest): AdminApiResult<WriteupDetailDto> {
        val idx = writeups.indexOfFirst { it.id == id }
        val updated = WriteupDetailDto(
            id = id,
            slug = request.slug,
            title = request.title,
            subtitle = request.subtitle,
            summary = request.summary,
            bodyMarkdown = request.bodyMarkdown,
            tags = request.tags,
            status = request.status,
            readingTimeMin = request.readingTimeMin,
            coverDocumentId = request.coverDocumentId,
            publishedAt = if (request.status == WriteupStatus.PUBLISHED) fixedNow else null,
            createdAt = fixedNow,
            updatedAt = fixedNow,
        )
        if (idx >= 0) writeups[idx] = updated else writeups.add(updated)
        return AdminApiResult.Success(updated)
    }

    override suspend fun deleteWriteup(id: String): AdminApiResult<Unit> = AdminApiResult.Success(Unit)

    override suspend fun getDocuments(options: QueryOptions): AdminApiResult<PagedResult<DocumentDto>> =
        AdminApiResult.Success(
            PagedResult.of(items = documents.toList(), totalCount = documents.size.toLong(), options = options),
        )

    override suspend fun presignDocumentUpload(request: PresignUploadRequest): AdminApiResult<PresignUploadResponse> {
        val docId = "doc-${documents.size + 1}"
        return AdminApiResult.Success(
            PresignUploadResponse(
                documentId = docId,
                storageKey = "documents/$docId/${request.originalFilename}",
                presignedPutUrl = "http://127.0.0.1:9000/portfolio-assets/$docId",
                expiresAt = fixedNow,
            ),
        )
    }

    override suspend fun uploadBinaryToPresignedUrl(
        presignedPutUrl: String,
        requiredHeaders: Map<String, String>,
        mimeType: String,
        bytes: ByteArray,
    ): AdminApiResult<Unit> {
        uploadedPutUrls.add(presignedPutUrl)
        uploadedBytes.add(bytes)
        return AdminApiResult.Success(Unit)
    }

    override suspend fun confirmDocumentUpload(request: ConfirmUploadRequest): AdminApiResult<DocumentDto> {
        val doc = DocumentDto(
            id = request.documentId,
            title = request.title,
            category = request.category,
            originalFilename = request.originalFilename,
            mimeType = request.declaredMimeType,
            sizeBytes = uploadedBytes.lastOrNull()?.size?.toLong() ?: 10L,
            sha256 = request.sha256,
            storageKey = request.storageKey,
            isPublic = request.isPublic,
            createdAt = fixedNow,
        )
        documents.add(doc)
        return AdminApiResult.Success(doc)
    }

    override suspend fun deleteDocument(id: String): AdminApiResult<Unit> = AdminApiResult.Success(Unit)
}
