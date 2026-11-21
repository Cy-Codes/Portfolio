package codes.cy.portfolio.presentation

import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.presentation.data.ApiResult
import kotlin.test.Test

class PortfolioStoresTest {

    @Test
    fun `MarkdownOutlineExtractor ignores fenced code comments and captures headings and demo blocks`() {
        val markdown = """
            # Architecture Overview

            ```kotlin
            # Not a heading inside code!
            val a = 1
            ```

            ## Adaptive Breakpoints
            ```demo:adaptive-breakpoints
            ```

            ### State Flow & UDF
        """.trimIndent()

        val outline = MarkdownOutlineExtractor.extract(markdown)
        assertEquals(3, outline.headings.size)
        assertEquals("Architecture Overview", outline.headings[0].title)
        assertEquals("architecture-overview", outline.headings[0].anchorId)
        assertEquals("Adaptive Breakpoints", outline.headings[1].title)
        assertEquals("State Flow & UDF", outline.headings[2].title)
        assertEquals(listOf("adaptive-breakpoints"), outline.embeddedDemoIds)
    }

    @Test
    fun `ProfileStore loads overview filters skills by category and handles errors`() = runTest {
        val fakeApi = FakePortfolioApi()
        val store = ProfileStoreFactory.create(
            api = fakeApi,
            analyticsSender = fakeApi,
            platform = ClientPlatform.DESKTOP,
            viewportTierProvider = { ViewportTier.LARGE },
        )

        store.subscribeAndTest {
            wait()
            val content = assertIs<ProfileState.Content>(states.value)
            assertEquals("CY", content.overview.profile?.fullName)
            assertEquals(2, content.filteredSkills.size)

            emit(ProfileIntent.SelectSkillCategory("Backend"))
            wait()
            val filtered = assertIs<ProfileState.Content>(states.value)
            assertEquals(listOf("Ktor"), filtered.filteredSkills.map { it.name })

            emit(ProfileIntent.OpenProjects)
            assertEquals(ProfileAction.NavigateToProjects, withTimeout(1000L) { actions.first() })

            fakeApi.nextError = ApiError.InternalError("Down")
            emit(ProfileIntent.Refresh)
            wait()
            assertIs<ProfileState.Error>(states.value)
        }
    }

    @Test
    fun `ProjectsStore supports filters pagination selection and analytics`() = runTest {
        val fakeApi = FakePortfolioApi()
        val store = ProjectsStoreFactory.create(
            api = fakeApi,
            analyticsSender = fakeApi,
            platform = ClientPlatform.WEB,
            viewportTierProvider = { ViewportTier.EXPANDED },
        )

        store.subscribeAndTest {
            wait()
            assertEquals(1, states.value.items.size)
            assertTrue(states.value.hasNextPage)

            emit(ProjectsIntent.LoadNextPage)
            wait()
            assertEquals(2, states.value.items.size)
            assertEquals(2, states.value.page)

            emit(ProjectsIntent.SelectProject("kmp-portfolio"))
            wait()
            assertEquals("kmp-portfolio", states.value.selectedProject?.slug)
            assertEquals(ProjectsAction.ProjectSelected("kmp-portfolio"), withTimeout(1000L) { actions.first() })
            assertTrue(
                fakeApi.recordedEvents.any {
                    it.eventType == AnalyticsEventType.PROJECT_CLICK && it.path == "/projects/kmp-portfolio"
                },
            )
        }
    }

    @Test
    fun `WriteupsStore and ReadArticleStore coordinate search reading and analytics`() = runTest {
        val fakeApi = FakePortfolioApi()
        val writeupsStore = WriteupsStoreFactory.create(
            api = fakeApi,
            analyticsSender = fakeApi,
            platform = ClientPlatform.ANDROID,
            viewportTierProvider = { ViewportTier.COMPACT },
        )

        writeupsStore.subscribeAndTest {
            wait()
            assertEquals(1, states.value.items.size)

            emit(WriteupsIntent.SelectWriteup("decompose-guide"))
            assertEquals(WriteupsAction.OpenWriteup("decompose-guide"), withTimeout(1000L) { actions.first() })
        }

        val readerStore = ReadArticleStoreFactory.create(
            slug = "decompose-guide",
            api = fakeApi,
            analyticsSender = fakeApi,
            platform = ClientPlatform.IOS,
            viewportTierProvider = { ViewportTier.MEDIUM },
        )

        readerStore.subscribeAndTest {
            wait()
            val content = assertIs<ReadArticleState.Content>(states.value)
            assertEquals(listOf("adaptive-breakpoints"), content.embeddedDemoIds)

            emit(ReadArticleIntent.Load)
            wait()
            assertTrue(fakeApi.recordedEvents.any { it.eventType == AnalyticsEventType.WRITEUP_READ })

            emit(ReadArticleIntent.OpenReaderHtmlVersion)
            assertEquals(
                ReadArticleAction.OpenExternalUrl("https://portfolio.example.test/read/decompose-guide"),
                withTimeout(1000L) { actions.first() },
            )
        }
    }

    @Test
    fun `ResumeDocsStore filters categories and triggers read-only GET downloads plus telemetry`() = runTest {
        val fakeApi = FakePortfolioApi()
        val store = ResumeDocsStoreFactory.create(
            api = fakeApi,
            analyticsSender = fakeApi,
            platform = ClientPlatform.WEB,
            viewportTierProvider = { ViewportTier.EXTRA_LARGE },
        )

        store.subscribeAndTest {
            wait()
            val loaded = assertIs<ResumeDocsState.Content>(states.value)
            assertEquals(2, loaded.filteredDocuments.size)

            emit(ResumeDocsIntent.SelectCategory(DocumentCategory.RESUME))
            wait()
            val resumesOnly = assertIs<ResumeDocsState.Content>(states.value)
            assertEquals(1, resumesOnly.filteredDocuments.size)

            val doc = resumesOnly.filteredDocuments.single()
            emit(ResumeDocsIntent.RequestDownload(doc))
            wait()
            val action = withTimeout(1000L) { actions.first() }
            assertEquals(
                ResumeDocsAction.TriggerDownload(
                    url = "https://portfolio.example.test/api/v1/public/documents/${doc.id}/download",
                    filename = doc.originalFilename,
                ),
                action,
            )
            assertTrue(
                fakeApi.recordedEvents.any {
                    it.eventType == AnalyticsEventType.DOCUMENT_DOWNLOAD && it.entityId == doc.id
                },
            )
        }
    }
}

private class FakePortfolioApi : PublicPortfolioApi, AnalyticsEventSender {
    private val fixedInstant: Instant = Instant.parse("2026-11-20T12:00:00Z")
    var nextError: ApiError? = null
    val recordedEvents: MutableList<TrackEventRequest> = mutableListOf()

    private val skill1 = SkillDto("s1", "Kotlin Multiplatform", "Mobile", isFeatured = true, sortOrder = 1)
    private val skill2 = SkillDto("s2", "Ktor", "Backend", isFeatured = true, sortOrder = 2)

    private val projects = listOf(
        ProjectSummaryDto("p1", "kmp-portfolio", "Portfolio", "Summary 1", status = ProjectStatus.ACTIVE, isFeatured = true, sortOrder = 1, updatedAt = fixedInstant),
        ProjectSummaryDto("p2", "pg-cms", "Postgres CMS", "Summary 2", status = ProjectStatus.ACTIVE, isFeatured = false, sortOrder = 2, updatedAt = fixedInstant),
    )

    private val docs = listOf(
        DocumentDto("d1", "Resume", DocumentCategory.RESUME, "resume.pdf", "application/pdf", 1200L, "a".repeat(64), "documents/d1/resume.pdf", true, 5L, fixedInstant),
        DocumentDto("d2", "Architecture Slides", DocumentCategory.SLIDES, "slides.pdf", "application/pdf", 4200L, "b".repeat(64), "documents/d2/slides.pdf", true, 2L, fixedInstant),
    )

    override suspend fun getPortfolioOverview(): ApiResult<PortfolioOverviewDto> {
        nextError?.let { err ->
            nextError = null
            return ApiResult.Failure(err)
        }
        return ApiResult.Success(
            PortfolioOverviewDto(
                profile = ProfileDto("CY", "Principal Engineer", "# Bio", updatedAt = fixedInstant),
                featuredSkills = listOf(skill1, skill2),
            ),
        )
    }

    override suspend fun getProjects(
        options: QueryOptions,
        featuredOnly: Boolean?,
        skillName: String?,
    ): ApiResult<PagedResult<ProjectSummaryDto>> {
        val pageItem = if (options.page == 1) listOf(projects[0]) else listOf(projects[1])
        return ApiResult.Success(
            PagedResult(
                items = pageItem,
                totalCount = 2L,
                page = options.page,
                pageSize = 1,
                totalPages = 2,
                hasPreviousPage = options.page > 1,
                hasNextPage = options.page < 2,
            ),
        )
    }

    override suspend fun getProjectBySlug(slug: String): ApiResult<ProjectDetailDto> =
        ApiResult.Success(
            ProjectDetailDto(
                id = "p1",
                slug = slug,
                title = "Portfolio",
                summary = "Summary",
                caseStudyMarkdown = "# Case Study",
                status = ProjectStatus.ACTIVE,
                isFeatured = true,
                sortOrder = 1,
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            ),
        )

    override suspend fun getWriteups(
        options: QueryOptions,
        tag: String?,
        searchQuery: String?,
    ): ApiResult<PagedResult<WriteupSummaryDto>> = ApiResult.Success(
        PagedResult.of(
            items = listOf(
                WriteupSummaryDto(
                    id = "w1",
                    slug = "decompose-guide",
                    title = "Decompose Guide",
                    summary = "Shared state",
                    tags = listOf("kotlin"),
                    status = WriteupStatus.PUBLISHED,
                    readingTimeMin = 5,
                    publishedAt = fixedInstant,
                    updatedAt = fixedInstant,
                ),
            ),
            totalCount = 1L,
            options = options,
        ),
    )

    override suspend fun getWriteupBySlug(slug: String): ApiResult<WriteupDetailDto> = ApiResult.Success(
        WriteupDetailDto(
            id = "w1",
            slug = slug,
            title = "Decompose Guide",
            summary = "Shared state",
            bodyMarkdown = "# Intro\n```demo:adaptive-breakpoints\n```\n",
            tags = listOf("kotlin"),
            status = WriteupStatus.PUBLISHED,
            readingTimeMin = 5,
            publishedAt = fixedInstant,
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        ),
    )

    override suspend fun getDocuments(options: QueryOptions): ApiResult<PagedResult<DocumentDto>> =
        ApiResult.Success(PagedResult.of(docs, docs.size.toLong(), options))

    override fun buildDocumentDownloadUrl(documentId: String): String =
        "https://portfolio.example.test/api/v1/public/documents/$documentId/download"

    override fun buildReaderHtmlUrl(slug: String): String =
        "https://portfolio.example.test/read/$slug"

    override suspend fun recordEvent(request: TrackEventRequest): ApiResult<Unit> {
        recordedEvents.add(request)
        return ApiResult.Success(Unit)
    }
}
