package codes.cy.portfolio.presentation.navigation

import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsAction
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalDecomposeApi::class)
interface RootComponent : WebNavigationOwner {
    sealed interface Child {
        data class Profile(val component: ProfileComponent) : Child
        data class Projects(val component: ProjectsPanelsComponent) : Child
        data class Writeups(val component: WriteupsPanelsComponent) : Child
        data class ResumeDocs(val component: ResumeDocsComponent) : Child
    }

    val childStack: Value<ChildStack<RootConfig, Child>>
    val activeDestination: Value<TopLevelDestination>
    val currentViewportTier: Value<ViewportTier>
    val externalUrlEffects: Flow<String>
    val downloadEffects: Flow<ResumeDocsAction.TriggerDownload>

    fun selectDestination(destination: TopLevelDestination)
    fun openProject(slug: String)
    fun openWriteup(slug: String)
    fun navigateBack()
    fun updateViewportTier(tier: ViewportTier)
}

@OptIn(ExperimentalDecomposeApi::class)
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val baseDependencies: PresentationDependencies,
    initialConfig: RootConfig = RootConfig.Profile,
    initialViewportTier: ViewportTier = ViewportTier.COMPACT,
) : RootComponent, ComponentContext by componentContext {

    private val viewportTierState = MutableValue(initialViewportTier)
    override val currentViewportTier: Value<ViewportTier> = viewportTierState

    private val dependencies: PresentationDependencies = baseDependencies.copy(
        viewportTierProvider = { viewportTierState.value },
    )

    private val navigation = StackNavigation<RootConfig>()
    private val uiScope = coroutineScope(dependencies.mainContext)
    private val _externalUrlEffects = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val externalUrlEffects: Flow<String> = _externalUrlEffects.asSharedFlow()

    private val _downloadEffects = MutableSharedFlow<ResumeDocsAction.TriggerDownload>(extraBufferCapacity = 4)
    override val downloadEffects: Flow<ResumeDocsAction.TriggerDownload> = _downloadEffects.asSharedFlow()

    override val childStack: Value<ChildStack<RootConfig, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = RootConfig.serializer(),
        initialConfiguration = initialConfig,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override val activeDestination: Value<TopLevelDestination> =
        childStack.map { stack -> PortfolioDeepLink.toDestination(stack.active.configuration) }

    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = childStack,
        serializer = RootConfig.serializer(),
        enableHistory = true,
        pathMapper = { child ->
            when (child.configuration) {
                is RootConfig.Profile -> "/"
                is RootConfig.Projects -> "/projects"
                is RootConfig.Writeups -> "/writeups"
                is RootConfig.ResumeDocs -> "/resume"
            }
        },
        childSelector = { child ->
            when (val instance = child.instance) {
                is RootComponent.Child.Projects -> instance.component
                is RootComponent.Child.Writeups -> instance.component
                is RootComponent.Child.Profile,
                is RootComponent.Child.ResumeDocs -> null
            }
        },
    )

    private fun createChild(
        config: RootConfig,
        childContext: ComponentContext,
    ): RootComponent.Child =
        when (config) {
            is RootConfig.Profile -> RootComponent.Child.Profile(
                component = DefaultProfileComponent(
                    componentContext = childContext,
                    dependencies = dependencies,
                    onOutput = { output ->
                        when (output) {
                            DefaultProfileComponent.ProfileOutput.OpenProjects ->
                                selectDestination(TopLevelDestination.PROJECTS)
                            DefaultProfileComponent.ProfileOutput.OpenWriteups ->
                                selectDestination(TopLevelDestination.WRITEUPS)
                            DefaultProfileComponent.ProfileOutput.OpenResumeDocs ->
                                selectDestination(TopLevelDestination.RESUME_DOCS)
                            is DefaultProfileComponent.ProfileOutput.OpenExternalUrl ->
                                emitExternalUrl(output.url)
                        }
                    },
                ),
            )

            is RootConfig.Projects -> RootComponent.Child.Projects(
                component = DefaultProjectsPanelsComponent(
                    componentContext = childContext,
                    dependencies = dependencies,
                    initialSlug = config.initialSlug,
                    onExternalUrl = ::emitExternalUrl,
                ),
            )

            is RootConfig.Writeups -> RootComponent.Child.Writeups(
                component = DefaultWriteupsPanelsComponent(
                    componentContext = childContext,
                    dependencies = dependencies,
                    initialSlug = config.initialSlug,
                    onExternalUrl = ::emitExternalUrl,
                ),
            )

            is RootConfig.ResumeDocs -> {
                val component = DefaultResumeDocsComponent(
                    componentContext = childContext,
                    dependencies = dependencies,
                )
                uiScope.launch {
                    component.downloadEffects.collect { effect ->
                        _downloadEffects.emit(effect)
                    }
                }
                RootComponent.Child.ResumeDocs(component = component)
            }
        }

    override fun selectDestination(destination: TopLevelDestination) {
        val existingConfig = childStack.value.items
            .firstOrNull { PortfolioDeepLink.toDestination(it.configuration) == destination }
            ?.configuration
        navigation.bringToFront(existingConfig ?: PortfolioDeepLink.toConfig(destination))
    }

    override fun openProject(slug: String) {
        val activeInstance = childStack.value.active.instance
        if (activeInstance is RootComponent.Child.Projects) {
            activeInstance.component.selectProject(slug)
        } else {
            navigation.bringToFront(RootConfig.Projects(initialSlug = slug))
        }
    }

    override fun openWriteup(slug: String) {
        val activeInstance = childStack.value.active.instance
        if (activeInstance is RootComponent.Child.Writeups) {
            activeInstance.component.openWriteup(slug)
        } else {
            navigation.bringToFront(RootConfig.Writeups(initialSlug = slug))
        }
    }

    override fun navigateBack() {
        navigation.pop()
    }

    override fun updateViewportTier(tier: ViewportTier) {
        viewportTierState.value = tier
    }

    private fun emitExternalUrl(url: String) {
        uiScope.launch {
            _externalUrlEffects.emit(url)
        }
    }

    companion object {
        fun fromDeepLinkPath(
            componentContext: ComponentContext,
            dependencies: PresentationDependencies,
            initialPath: String?,
            initialViewportTier: ViewportTier = ViewportTier.COMPACT,
        ): DefaultRootComponent = DefaultRootComponent(
            componentContext = componentContext,
            baseDependencies = dependencies,
            initialConfig = PortfolioDeepLink.fromPath(initialPath),
            initialViewportTier = initialViewportTier,
        )
    }
}
