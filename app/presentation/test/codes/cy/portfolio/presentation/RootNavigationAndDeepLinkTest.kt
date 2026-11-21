package codes.cy.portfolio.presentation

import codes.cy.portfolio.core.model.ClientPlatform
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
import codes.cy.portfolio.presentation.features.profile.ProfileIntent
import codes.cy.portfolio.presentation.navigation.DefaultRootComponent
import codes.cy.portfolio.presentation.navigation.PortfolioDeepLink
import codes.cy.portfolio.presentation.navigation.PresentationDependencies
import codes.cy.portfolio.presentation.navigation.RootComponent
import codes.cy.portfolio.presentation.navigation.RootConfig
import codes.cy.portfolio.presentation.navigation.TopLevelDestination
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalDecomposeApi::class, ExperimentalCoroutinesApi::class)
class RootNavigationAndDeepLinkTest {

    @Test
    fun `PortfolioDeepLink maps URLs to RootConfig and back deterministically`() {
        assertEquals(RootConfig.Profile, PortfolioDeepLink.fromPath("/"))
        assertEquals(RootConfig.Profile, PortfolioDeepLink.fromPath("/about"))
        assertEquals(RootConfig.Projects(), PortfolioDeepLink.fromPath("/projects"))
        assertEquals(RootConfig.Projects("kmp-portfolio"), PortfolioDeepLink.fromPath("/projects/kmp-portfolio?tab=1"))
        assertEquals(RootConfig.Writeups("decompose-guide"), PortfolioDeepLink.fromPath("/writeups/decompose-guide#intro"))
        assertEquals(RootConfig.ResumeDocs, PortfolioDeepLink.fromPath("/resume"))
        assertEquals(RootConfig.Projects(initialSlug = null), PortfolioDeepLink.fromPath("/projects/INVALID_SLUG!"))
        assertEquals(RootConfig.Profile, PortfolioDeepLink.fromPath("https://cy.codes"))
        assertEquals(RootConfig.Profile, PortfolioDeepLink.fromPath("https://cy.codes/"))
        assertEquals(
            RootConfig.Writeups("decompose-guide"),
            PortfolioDeepLink.fromPath("https://cy.codes/writeups/decompose-guide?ref=1#intro"),
        )
        assertEquals(
            RootConfig.Projects("kmp-portfolio"),
            PortfolioDeepLink.fromPath("http://127.0.0.1:8080/projects/kmp-portfolio"),
        )

        assertEquals("/", PortfolioDeepLink.toPath(RootConfig.Profile))
        assertEquals("/projects/kmp-portfolio", PortfolioDeepLink.toPath(RootConfig.Projects("kmp-portfolio")))
        assertEquals("/writeups/decompose-guide", PortfolioDeepLink.toPath(RootConfig.Writeups("decompose-guide")))
        assertEquals("/resume", PortfolioDeepLink.toPath(RootConfig.ResumeDocs))
    }

    @Test
    fun `DefaultRootComponent drives ChildStack ChildPanels WebNavigation paths and outputs cleanly`() = runTest {
        val lifecycle = LifecycleRegistry()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val navFakeApi = NavigationFakeApi()
        val dependencies = PresentationDependencies(
            api = navFakeApi,
            analyticsSender = navFakeApi,
            platform = ClientPlatform.WEB,
            viewportTierProvider = { ViewportTier.LARGE },
            mainContext = dispatcher,
        )

        val root = DefaultRootComponent.fromDeepLinkPath(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            dependencies = dependencies,
            initialPath = "/writeups/decompose-guide",
            initialViewportTier = ViewportTier.LARGE,
        )
        lifecycle.resume()

        assertEquals(TopLevelDestination.WRITEUPS, root.activeDestination.value)
        val writeupsChild = assertIs<RootComponent.Child.Writeups>(root.childStack.value.active.instance)
        assertNotNull(writeupsChild.component.panels.value.details)
        assertNotNull(writeupsChild.component.panels.value.extra)

        writeupsChild.component.setMode(ChildPanelsMode.TRIPLE)
        assertEquals(ChildPanelsMode.TRIPLE, writeupsChild.component.panels.value.mode)

        // Check nested WebNavigation history emits /writeups/decompose-guide
        assertEquals("/writeups/decompose-guide", buildFullWebPath(root.webNavigation.history.value.last()))

        // Hide extra TOC panel
        writeupsChild.component.setSupportingTocVisible(false)
        assertNull(writeupsChild.component.panels.value.extra)

        // Switch via top-level tab and navigate via Profile output
        root.selectDestination(TopLevelDestination.PROFILE)
        assertEquals(TopLevelDestination.PROFILE, root.activeDestination.value)
        val profileChild = assertIs<RootComponent.Child.Profile>(root.childStack.value.active.instance)
        profileChild.component.onIntent(ProfileIntent.OpenProjects)
        testScheduler.advanceUntilIdle()
        assertEquals(TopLevelDestination.PROJECTS, root.activeDestination.value)

        // Open specific project detail inside ChildPanels and verify path
        root.openProject("kmp-portfolio")
        testScheduler.advanceUntilIdle()
        assertEquals("/projects/kmp-portfolio", buildFullWebPath(root.webNavigation.history.value.last()))

        root.updateViewportTier(ViewportTier.EXTRA_LARGE)
        assertEquals(ViewportTier.EXTRA_LARGE, root.currentViewportTier.value)

        root.navigateBack()
        assertEquals(TopLevelDestination.PROFILE, root.activeDestination.value)

        lifecycle.destroy()
    }

    private fun buildFullWebPath(item: com.arkivanov.decompose.router.webhistory.WebNavigation.HistoryItem<*>): String {
        val childItem = item.child?.webNavigation?.history?.value?.lastOrNull()
        val childPath = childItem?.let { buildFullWebPath(it) }.orEmpty()
        return (item.path.trimEnd('/') + childPath).ifEmpty { "/" }
    }
}

private class NavigationFakeApi : PublicPortfolioApi, AnalyticsEventSender {
    private val fixedInstant: Instant = Instant.parse("2026-11-20T12:00:00Z")

    override suspend fun getPortfolioOverview() = ApiResult.Success(PortfolioOverviewDto(profile = null))

    override suspend fun getProjects(options: QueryOptions, featuredOnly: Boolean?, skillName: String?) =
        ApiResult.Success(PagedResult.of(emptyList<ProjectSummaryDto>(), 0L, options))

    override suspend fun getProjectBySlug(slug: String) = ApiResult.Success(
        ProjectDetailDto(
            id = "p1",
            slug = slug,
            title = "Portfolio",
            summary = "Summary",
            caseStudyMarkdown = "# Detail",
            status = ProjectStatus.ACTIVE,
            isFeatured = true,
            sortOrder = 1,
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        ),
    )

    override suspend fun getWriteups(options: QueryOptions, tag: String?, searchQuery: String?) =
        ApiResult.Success(PagedResult.of(emptyList<WriteupSummaryDto>(), 0L, options))

    override suspend fun getWriteupBySlug(slug: String) = ApiResult.Success(
        WriteupDetailDto(
            id = "w1",
            slug = slug,
            title = "Decompose Guide",
            summary = "Summary",
            bodyMarkdown = "# Heading\n```demo:sample\n```",
            tags = emptyList(),
            status = WriteupStatus.PUBLISHED,
            readingTimeMin = 3,
            publishedAt = fixedInstant,
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        ),
    )

    override suspend fun getDocuments(options: QueryOptions) =
        ApiResult.Success(PagedResult.of(emptyList<DocumentDto>(), 0L, options))

    override fun buildDocumentDownloadUrl(documentId: String) = "/api/v1/public/documents/$documentId/download"

    override fun buildReaderHtmlUrl(slug: String) = "/read/$slug"

    override suspend fun recordEvent(request: TrackEventRequest): ApiResult<Unit> = ApiResult.Success(Unit)
}
