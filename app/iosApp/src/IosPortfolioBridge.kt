package codes.cy.portfolio

import androidx.compose.ui.window.ComposeUIViewController
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.features.profile.ProfileIntent
import codes.cy.portfolio.presentation.features.projects.ProjectsIntent
import codes.cy.portfolio.presentation.features.readarticle.ReadArticleIntent
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsIntent
import codes.cy.portfolio.presentation.features.writeups.WriteupsIntent
import codes.cy.portfolio.presentation.navigation.DefaultRootComponent
import codes.cy.portfolio.presentation.navigation.PortfolioDeepLink
import codes.cy.portfolio.presentation.navigation.PresentationDependencies
import codes.cy.portfolio.presentation.navigation.ProfileComponent
import codes.cy.portfolio.presentation.navigation.ProjectsPanelsComponent
import codes.cy.portfolio.presentation.navigation.ResumeDocsComponent
import codes.cy.portfolio.presentation.navigation.RootComponent
import codes.cy.portfolio.presentation.navigation.RootConfig
import codes.cy.portfolio.presentation.navigation.TopLevelDestination
import codes.cy.portfolio.presentation.navigation.WriteupsPanelsComponent
import codes.cy.portfolio.ui.demos.DemoIslandEntryPoint
import codes.cy.portfolio.ui.demos.DemoRegistry
import codes.cy.portfolio.ui.markdown.MarkdownSegment
import codes.cy.portfolio.ui.markdown.MarkdownSegmentParser
import codes.cy.portfolio.ui.platform.displayLabel
import codes.cy.portfolio.ui.platform.formatByteSize
import codes.cy.portfolio.ui.platform.formatMonthYear
import codes.cy.portfolio.ui.platform.formatShortDate
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import platform.UIKit.UIViewController

private const val DEFAULT_IOS_API_BASE_URL: String = "http://127.0.0.1:8080"
private const val HEIGHT_ADAPTIVE_BREAKPOINTS_PT: Double = 360.0
private const val HEIGHT_STATE_FLOW_UDF_PT: Double = 300.0
private const val HEIGHT_PAGINATION_GUARD_PT: Double = 280.0
private const val HEIGHT_DEFAULT_DEMO_PT: Double = 180.0

/**
 * Owns the Decompose [RootComponent] and Essenty [LifecycleRegistry] for the native SwiftUI host.
 */
class IosPortfolioAppHost internal constructor(
    dependencies: PresentationDependencies,
    initialPath: String?,
    private val onExternalUrl: (String) -> Unit,
    private val onDownload: (String, String) -> Unit,
    effectContext: CoroutineContext,
) {
    constructor(
        apiBaseUrl: String = DEFAULT_IOS_API_BASE_URL,
        initialPath: String? = null,
        onExternalUrl: (String) -> Unit = {},
        onDownload: (String, String) -> Unit = { _, _ -> },
    ) : this(
        dependencies = PresentationDependencies.fromBaseUrl(
            baseUrl = apiBaseUrl,
            platform = ClientPlatform.IOS,
            viewportTierProvider = { ViewportTier.COMPACT },
        ),
        initialPath = initialPath,
        onExternalUrl = onExternalUrl,
        onDownload = onDownload,
        effectContext = Dispatchers.Main.immediate,
    )

    private val lifecycle = LifecycleRegistry()
    private val effectScope = CoroutineScope(SupervisorJob() + effectContext)

    val rootComponent: RootComponent = DefaultRootComponent.fromDeepLinkPath(
        componentContext = DefaultComponentContext(lifecycle = lifecycle),
        dependencies = dependencies,
        initialPath = initialPath,
        initialViewportTier = ViewportTier.COMPACT,
    )

    init {
        effectScope.launch {
            rootComponent.externalUrlEffects.collect { url ->
                onExternalUrl(url)
            }
        }
        effectScope.launch {
            rootComponent.downloadEffects.collect { effect ->
                onDownload(effect.url, effect.filename)
            }
        }
    }

    fun onActive() {
        lifecycle.resume()
    }

    fun onBackground() {
        lifecycle.stop()
    }

    fun onDestroy() {
        effectScope.cancel()
        lifecycle.destroy()
    }

    fun handleDeepLink(rawUrl: String) {
        when (val target = PortfolioDeepLink.fromPath(rawUrl)) {
            is RootConfig.Profile -> rootComponent.selectDestination(TopLevelDestination.PROFILE)
            is RootConfig.Projects -> {
                val slug = target.initialSlug
                if (slug != null) {
                    rootComponent.openProject(slug)
                } else {
                    rootComponent.selectDestination(TopLevelDestination.PROJECTS)
                }
            }
            is RootConfig.Writeups -> {
                val slug = target.initialSlug
                if (slug != null) {
                    rootComponent.openWriteup(slug)
                } else {
                    rootComponent.selectDestination(TopLevelDestination.WRITEUPS)
                }
            }
            is RootConfig.ResumeDocs -> rootComponent.selectDestination(TopLevelDestination.RESUME_DOCS)
        }
    }
}

/**
 * Clean Swift-friendly helpers exposing typed FlowMVI dispatches, shared Markdown segment
 * parsing, pure display formatters, and inline Compose demo islands (ADR 3).
 */
object IosPortfolioBridge {
    private const val DEMO_ADAPTIVE_BREAKPOINTS: String = "adaptive-breakpoints"
    private const val DEMO_STATE_FLOW_UDF: String = "state-flow-udf"
    private const val DEMO_PAGINATION_GUARD: String = "pagination-guard"

    fun dispatchProfile(component: ProfileComponent, intent: ProfileIntent) {
        component.onIntent(intent)
    }

    fun dispatchProjects(component: ProjectsPanelsComponent, intent: ProjectsIntent) {
        component.onIntent(intent)
    }

    fun dispatchWriteups(child: WriteupsPanelsComponent.WriteupsListChild, intent: WriteupsIntent) {
        child.onIntent(intent)
    }

    fun dispatchReadArticle(component: WriteupsPanelsComponent.ReadArticleComponent, intent: ReadArticleIntent) {
        component.onIntent(intent)
    }

    fun dispatchResumeDocs(component: ResumeDocsComponent, intent: ResumeDocsIntent) {
        component.onIntent(intent)
    }

    fun parseMarkdownSegments(markdown: String): List<MarkdownSegment> =
        MarkdownSegmentParser.parse(markdown)

    fun formatByteSize(bytes: Long): String = bytes.formatByteSize()

    fun formatLocalDate(date: LocalDate): String = date.formatMonthYear()

    fun formatInstant(instant: Instant): String = instant.formatShortDate()

    fun projectStatusLabel(status: ProjectStatus): String = status.displayLabel()

    fun documentCategoryLabel(category: DocumentCategory): String = category.displayLabel()

    fun isKnownDemo(demoId: String): Boolean = DemoRegistry.resolve(demoId) != null

    fun recommendedDemoHeightPt(demoId: String): Double =
        when (demoId.trim()) {
            DEMO_ADAPTIVE_BREAKPOINTS -> HEIGHT_ADAPTIVE_BREAKPOINTS_PT
            DEMO_STATE_FLOW_UDF -> HEIGHT_STATE_FLOW_UDF_PT
            DEMO_PAGINATION_GUARD -> HEIGHT_PAGINATION_GUARD_PT
            else -> HEIGHT_DEFAULT_DEMO_PT
        }

    fun createDemoIslandViewController(
        demoId: String,
        darkTheme: Boolean,
    ): UIViewController =
        ComposeUIViewController {
            DemoIslandEntryPoint(
                demoId = demoId,
                darkTheme = darkTheme,
            )
        }
}
