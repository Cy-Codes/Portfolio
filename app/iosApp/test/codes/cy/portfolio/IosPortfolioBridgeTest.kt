package codes.cy.portfolio

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.presentation.data.AnalyticsEventSender
import codes.cy.portfolio.presentation.data.ApiResult
import codes.cy.portfolio.presentation.data.PublicPortfolioApi
import codes.cy.portfolio.presentation.navigation.PresentationDependencies
import codes.cy.portfolio.presentation.navigation.RootComponent
import codes.cy.portfolio.presentation.navigation.TopLevelDestination
import codes.cy.portfolio.ui.markdown.MarkdownSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate

class IosPortfolioBridgeTest {

    @Test
    fun `IosPortfolioBridge exposes Markdown segments demo metadata and formatters cleanly`() {
        val segments = IosPortfolioBridge.parseMarkdownSegments(
            "# Heading\n\n```demo:adaptive-breakpoints\n```\n\nTrailing text",
        )
        assertEquals(3, segments.size)
        assertIs<MarkdownSegment.Prose>(segments[0])
        val demo = assertIs<MarkdownSegment.DemoIsland>(segments[1])
        assertEquals("adaptive-breakpoints", demo.demoId)
        assertIs<MarkdownSegment.Prose>(segments[2])

        assertTrue(IosPortfolioBridge.isKnownDemo("adaptive-breakpoints"))
        assertTrue(IosPortfolioBridge.isKnownDemo("state-flow-udf"))
        assertTrue(IosPortfolioBridge.isKnownDemo("pagination-guard"))
        assertFalse(IosPortfolioBridge.isKnownDemo("missing-demo"))

        assertEquals(360.0, IosPortfolioBridge.recommendedDemoHeightPt("adaptive-breakpoints"))
        assertEquals(180.0, IosPortfolioBridge.recommendedDemoHeightPt("unknown-demo"))

        assertEquals("1.5 KB", IosPortfolioBridge.formatByteSize(1536L))
        assertEquals("Mar 2024", IosPortfolioBridge.formatLocalDate(LocalDate.parse("2024-03-01")))
        assertEquals("Active", IosPortfolioBridge.projectStatusLabel(ProjectStatus.ACTIVE))
        assertEquals("Resume", IosPortfolioBridge.documentCategoryLabel(DocumentCategory.RESUME))
    }

    @Test
    fun `IosPortfolioAppHost handles deep links across Stack and ChildPanels cleanly`() {
        val fakeApi = OfflineIosFakeApi()
        val dependencies = PresentationDependencies(
            api = fakeApi,
            analyticsSender = fakeApi,
            platform = ClientPlatform.IOS,
            viewportTierProvider = { ViewportTier.COMPACT },
            mainContext = Dispatchers.Unconfined,
        )
        val host = IosPortfolioAppHost(
            dependencies = dependencies,
            initialPath = "/",
            onExternalUrl = {},
            onDownload = { _, _ -> },
            effectContext = Dispatchers.Unconfined,
        )

        host.onActive()
        assertEquals(TopLevelDestination.PROFILE, host.rootComponent.activeDestination.value)

        host.handleDeepLink("/projects/adaptive-kmp-portfolio")
        assertEquals(TopLevelDestination.PROJECTS, host.rootComponent.activeDestination.value)
        assertIs<RootComponent.Child.Projects>(host.rootComponent.childStack.value.active.instance)

        host.handleDeepLink("/writeups/decompose-flowmvi-with-swiftui-and-wasm")
        assertEquals(TopLevelDestination.WRITEUPS, host.rootComponent.activeDestination.value)
        assertIs<RootComponent.Child.Writeups>(host.rootComponent.childStack.value.active.instance)

        host.onBackground()
        host.onDestroy()
    }
}

private class OfflineIosFakeApi : PublicPortfolioApi, AnalyticsEventSender {
    private val fixedInstant = Instant.parse("2026-11-21T00:00:00Z")

    override suspend fun getPortfolioOverview(): ApiResult<PortfolioOverviewDto> =
        ApiResult.Success(PortfolioOverviewDto(profile = null))

    override suspend fun getProjects(
        options: QueryOptions,
        featuredOnly: Boolean?,
        skillName: String?,
    ): ApiResult<PagedResult<ProjectSummaryDto>> =
        ApiResult.Success(PagedResult.of(emptyList(), 0L, options))

    override suspend fun getProjectBySlug(slug: String): ApiResult<ProjectDetailDto> =
        ApiResult.Failure(ApiError.NotFound("Not found", "project", slug))

    override suspend fun getWriteups(
        options: QueryOptions,
        tag: String?,
        searchQuery: String?,
    ): ApiResult<PagedResult<WriteupSummaryDto>> =
        ApiResult.Success(PagedResult.of(emptyList(), 0L, options))

    override suspend fun getWriteupBySlug(slug: String): ApiResult<WriteupDetailDto> =
        ApiResult.Success(
            WriteupDetailDto(
                id = "w1",
                slug = slug,
                title = "Sample",
                summary = "Summary",
                bodyMarkdown = "# Intro",
                tags = emptyList(),
                status = WriteupStatus.PUBLISHED,
                readingTimeMin = 2,
                publishedAt = fixedInstant,
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            ),
        )

    override suspend fun getDocuments(options: QueryOptions): ApiResult<PagedResult<DocumentDto>> =
        ApiResult.Success(PagedResult.of(emptyList<DocumentDto>(), 0L, options))

    override fun buildDocumentDownloadUrl(documentId: String): String =
        "/api/v1/public/documents/$documentId/download"

    override fun buildReaderHtmlUrl(slug: String): String = "/read/$slug"

    override suspend fun recordEvent(request: TrackEventRequest): ApiResult<Unit> = ApiResult.Success(Unit)
}
