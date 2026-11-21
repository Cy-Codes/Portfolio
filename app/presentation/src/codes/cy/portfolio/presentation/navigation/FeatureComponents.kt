package codes.cy.portfolio.presentation.navigation

import codes.cy.portfolio.presentation.features.profile.ProfileAction
import codes.cy.portfolio.presentation.features.profile.ProfileIntent
import codes.cy.portfolio.presentation.features.profile.ProfileState
import codes.cy.portfolio.presentation.features.profile.ProfileStoreFactory.configureStore
import codes.cy.portfolio.presentation.features.projects.ProjectsAction
import codes.cy.portfolio.presentation.features.projects.ProjectsIntent
import codes.cy.portfolio.presentation.features.projects.ProjectsState
import codes.cy.portfolio.presentation.features.projects.ProjectsStoreFactory.configureStore
import codes.cy.portfolio.presentation.features.readarticle.ReadArticleAction
import codes.cy.portfolio.presentation.features.readarticle.ReadArticleIntent
import codes.cy.portfolio.presentation.features.readarticle.ReadArticleState
import codes.cy.portfolio.presentation.features.readarticle.ReadArticleStoreFactory.configureStore
import codes.cy.portfolio.presentation.features.readarticle.TocHeading
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsAction
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsIntent
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsState
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsStoreFactory.configureStore
import codes.cy.portfolio.presentation.features.writeups.WriteupsAction
import codes.cy.portfolio.presentation.features.writeups.WriteupsIntent
import codes.cy.portfolio.presentation.features.writeups.WriteupsState
import codes.cy.portfolio.presentation.features.writeups.WriteupsStoreFactory.configureStore
import codes.cy.portfolio.presentation.plugins.asDecomposeValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.panels.ChildPanels
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import com.arkivanov.decompose.router.panels.Panels
import com.arkivanov.decompose.router.panels.PanelsNavigation
import com.arkivanov.decompose.router.panels.activateDetails
import com.arkivanov.decompose.router.panels.activateExtra
import com.arkivanov.decompose.router.panels.childPanels
import com.arkivanov.decompose.router.panels.childPanelsWebNavigation
import com.arkivanov.decompose.router.panels.dismissDetails
import com.arkivanov.decompose.router.panels.dismissExtra
import com.arkivanov.decompose.router.panels.navigate
import com.arkivanov.decompose.router.panels.setMode
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.essenty.api.DelicateRetainedApi
import pro.respawn.flowmvi.essenty.dsl.retainedScope
import pro.respawn.flowmvi.essenty.dsl.retainedStore

// ==================== Profile ====================

interface ProfileComponent {
    val state: Value<ProfileState>
    val store: Store<ProfileState, ProfileIntent, ProfileAction>
    fun onIntent(intent: ProfileIntent)
}

@OptIn(DelicateRetainedApi::class)
class DefaultProfileComponent(
    componentContext: ComponentContext,
    dependencies: PresentationDependencies,
    private val onOutput: (ProfileOutput) -> Unit,
) : ProfileComponent, ComponentContext by componentContext {

    sealed interface ProfileOutput {
        data object OpenProjects : ProfileOutput
        data object OpenWriteups : ProfileOutput
        data object OpenResumeDocs : ProfileOutput
        data class OpenExternalUrl(val url: String) : ProfileOutput
    }

    override val store: Store<ProfileState, ProfileIntent, ProfileAction> =
        instanceKeeper.retainedStore<ProfileState, ProfileIntent, ProfileAction>(
            initial = ProfileState.Loading,
            scope = instanceKeeper.retainedScope(context = dependencies.mainContext),
        ) {
            configureStore(
                api = dependencies.api,
                analyticsSender = dependencies.analyticsSender,
                platform = dependencies.platform,
                viewportTierProvider = dependencies.viewportTierProvider,
                debuggable = dependencies.debuggable,
            )
        }

    override val state: Value<ProfileState> = store.asDecomposeValue(
        initialState = ProfileState.Loading,
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
        onAction = { action ->
            when (action) {
                is ProfileAction.NavigateToProjects -> onOutput(ProfileOutput.OpenProjects)
                is ProfileAction.NavigateToWriteups -> onOutput(ProfileOutput.OpenWriteups)
                is ProfileAction.NavigateToResumeDocs -> onOutput(ProfileOutput.OpenResumeDocs)
                is ProfileAction.OpenExternalUrl -> onOutput(ProfileOutput.OpenExternalUrl(action.url))
            }
        },
    )

    override fun onIntent(intent: ProfileIntent) {
        store.intent(intent)
    }
}

// ==================== Projects Adaptive ChildPanels ====================

@OptIn(ExperimentalDecomposeApi::class)
interface ProjectsPanelsComponent : WebNavigationOwner {
    val panels: Value<ChildPanels<ProjectsListConfig, ProjectsListChild, ProjectDetailConfig, ProjectDetailChild, Nothing, Nothing>>
    val state: Value<ProjectsState>
    val store: Store<ProjectsState, ProjectsIntent, ProjectsAction>

    fun setMode(mode: ChildPanelsMode)
    fun selectProject(slug: String)
    fun clearSelection()
    fun onIntent(intent: ProjectsIntent)

    @Serializable
    data object ProjectsListConfig

    @Serializable
    data class ProjectDetailConfig(val slug: String)

    data class ProjectsListChild(
        val state: Value<ProjectsState>,
        val onIntent: (ProjectsIntent) -> Unit,
    )

    data class ProjectDetailChild(
        val slug: String,
        val state: Value<ProjectsState>,
        val onIntent: (ProjectsIntent) -> Unit,
    )
}

@OptIn(ExperimentalDecomposeApi::class, ExperimentalSerializationApi::class, DelicateRetainedApi::class)
class DefaultProjectsPanelsComponent(
    componentContext: ComponentContext,
    dependencies: PresentationDependencies,
    initialSlug: String? = null,
    private val onExternalUrl: (String) -> Unit = {},
) : ProjectsPanelsComponent, ComponentContext by componentContext {

    private val navigation = PanelsNavigation<ProjectsPanelsComponent.ProjectsListConfig, ProjectsPanelsComponent.ProjectDetailConfig, Nothing>()

    override val store: Store<ProjectsState, ProjectsIntent, ProjectsAction> =
        instanceKeeper.retainedStore<ProjectsState, ProjectsIntent, ProjectsAction>(
            initial = ProjectsState(selectedSlug = initialSlug),
            scope = instanceKeeper.retainedScope(context = dependencies.mainContext),
        ) {
            configureStore(
                api = dependencies.api,
                analyticsSender = dependencies.analyticsSender,
                platform = dependencies.platform,
                viewportTierProvider = dependencies.viewportTierProvider,
                initialSlug = initialSlug,
                debuggable = dependencies.debuggable,
            )
        }

    override val state: Value<ProjectsState> = store.asDecomposeValue(
        initialState = ProjectsState(selectedSlug = initialSlug),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
        onAction = { action ->
            when (action) {
                is ProjectsAction.ProjectSelected ->
                    navigation.activateDetails(ProjectsPanelsComponent.ProjectDetailConfig(action.slug))
                is ProjectsAction.SelectionCleared ->
                    navigation.dismissDetails()
                is ProjectsAction.OpenExternalUrl -> onExternalUrl(action.url)
                is ProjectsAction.ShowError -> Unit
            }
        },
    )

    override val panels: Value<
        ChildPanels<
            ProjectsPanelsComponent.ProjectsListConfig,
            ProjectsPanelsComponent.ProjectsListChild,
            ProjectsPanelsComponent.ProjectDetailConfig,
            ProjectsPanelsComponent.ProjectDetailChild,
            Nothing,
            Nothing,
            >,
        > = childPanels(
        source = navigation,
        serializers = ProjectsPanelsComponent.ProjectsListConfig.serializer() to
            ProjectsPanelsComponent.ProjectDetailConfig.serializer(),
        initialPanels = {
            Panels(
                main = ProjectsPanelsComponent.ProjectsListConfig,
                details = initialSlug?.let { ProjectsPanelsComponent.ProjectDetailConfig(it) },
            )
        },
        handleBackButton = true,
        mainFactory = { _, _ ->
            ProjectsPanelsComponent.ProjectsListChild(
                state = state,
                onIntent = ::onIntent,
            )
        },
        detailsFactory = { config, _ ->
            ProjectsPanelsComponent.ProjectDetailChild(
                slug = config.slug,
                state = state,
                onIntent = ::onIntent,
            )
        },
    )

    override val webNavigation: WebNavigation<*> = childPanelsWebNavigation(
        navigator = navigation,
        panels = panels,
        serializers = ProjectsPanelsComponent.ProjectsListConfig.serializer() to
            ProjectsPanelsComponent.ProjectDetailConfig.serializer(),
        pathMapper = { currentPanels ->
            currentPanels.details?.configuration?.slug?.let { "/$it" }
        },
    )

    override fun setMode(mode: ChildPanelsMode) {
        navigation.setMode(mode)
    }

    override fun selectProject(slug: String) {
        store.intent(ProjectsIntent.SelectProject(slug))
    }

    override fun clearSelection() {
        store.intent(ProjectsIntent.ClearSelection)
    }

    override fun onIntent(intent: ProjectsIntent) {
        store.intent(intent)
    }
}

// ==================== Writeups + ReadArticle + TOC ChildPanels ====================

@OptIn(ExperimentalDecomposeApi::class)
interface WriteupsPanelsComponent : WebNavigationOwner {
    val panels: Value<
        ChildPanels<
            WriteupsListConfig,
            WriteupsListChild,
            ReadArticleConfig,
            ReadArticleComponent,
            ArticleExtraConfig,
            ArticleExtraChild,
            >,
        >

    fun setMode(mode: ChildPanelsMode)
    fun openWriteup(slug: String)
    fun closeWriteup()
    fun setSupportingTocVisible(visible: Boolean)

    @Serializable
    data object WriteupsListConfig

    @Serializable
    data class ReadArticleConfig(val slug: String)

    @Serializable
    data class ArticleExtraConfig(val slug: String)

    interface WriteupsListChild {
        val state: Value<WriteupsState>
        val store: Store<WriteupsState, WriteupsIntent, WriteupsAction>
        fun onIntent(intent: WriteupsIntent)
    }

    interface ReadArticleComponent {
        val slug: String
        val state: Value<ReadArticleState>
        val store: Store<ReadArticleState, ReadArticleIntent, ReadArticleAction>
        fun onIntent(intent: ReadArticleIntent)
    }

    data class ArticleExtraChild(
        val slug: String,
        val tocHeadings: Value<List<TocHeading>>,
        val embeddedDemoIds: Value<List<String>>,
    )
}

@OptIn(ExperimentalDecomposeApi::class, DelicateRetainedApi::class)
private class DefaultWriteupsListChild(
    componentContext: ComponentContext,
    dependencies: PresentationDependencies,
    initialSlug: String?,
    private val onOpenWriteup: (String) -> Unit,
    private val onSelectionCleared: () -> Unit,
) : WriteupsPanelsComponent.WriteupsListChild, ComponentContext by componentContext {

    override val store: Store<WriteupsState, WriteupsIntent, WriteupsAction> =
        instanceKeeper.retainedStore<WriteupsState, WriteupsIntent, WriteupsAction>(
            initial = WriteupsState(selectedSlug = initialSlug),
            scope = instanceKeeper.retainedScope(context = dependencies.mainContext),
        ) {
            configureStore(
                api = dependencies.api,
                analyticsSender = dependencies.analyticsSender,
                platform = dependencies.platform,
                viewportTierProvider = dependencies.viewportTierProvider,
                debuggable = dependencies.debuggable,
            )
        }

    override val state: Value<WriteupsState> = store.asDecomposeValue(
        initialState = WriteupsState(selectedSlug = initialSlug),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
        onAction = { action ->
            when (action) {
                is WriteupsAction.OpenWriteup -> onOpenWriteup(action.slug)
                is WriteupsAction.SelectionCleared -> onSelectionCleared()
                is WriteupsAction.ShowError -> Unit
            }
        },
    )

    override fun onIntent(intent: WriteupsIntent) {
        store.intent(intent)
    }
}

@OptIn(DelicateRetainedApi::class)
private class DefaultReadArticleComponent(
    componentContext: ComponentContext,
    override val slug: String,
    dependencies: PresentationDependencies,
    private val onOutlineUpdated: (String, List<TocHeading>, List<String>) -> Unit,
    private val onNavigateBack: () -> Unit,
    private val onOpenExternalUrl: (String) -> Unit,
) : WriteupsPanelsComponent.ReadArticleComponent, ComponentContext by componentContext {

    override val store: Store<ReadArticleState, ReadArticleIntent, ReadArticleAction> =
        instanceKeeper.retainedStore<ReadArticleState, ReadArticleIntent, ReadArticleAction>(
            initial = ReadArticleState.Loading(slug = slug),
            key = "ReadArticleStore-$slug",
            scope = instanceKeeper.retainedScope(context = dependencies.mainContext),
        ) {
            configureStore(
                slug = slug,
                api = dependencies.api,
                analyticsSender = dependencies.analyticsSender,
                platform = dependencies.platform,
                viewportTierProvider = dependencies.viewportTierProvider,
                debuggable = dependencies.debuggable,
            )
        }

    override val state: Value<ReadArticleState> = store.asDecomposeValue(
        initialState = ReadArticleState.Loading(slug = slug),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
        onAction = { action ->
            when (action) {
                is ReadArticleAction.NavigateBack -> onNavigateBack()
                is ReadArticleAction.OpenExternalUrl -> onOpenExternalUrl(action.url)
            }
        },
    )

    init {
        state.subscribe { articleState ->
            if (articleState is ReadArticleState.Content) {
                onOutlineUpdated(articleState.slug, articleState.tableOfContents, articleState.embeddedDemoIds)
            }
        }
    }

    override fun onIntent(intent: ReadArticleIntent) {
        store.intent(intent)
    }
}

@OptIn(ExperimentalDecomposeApi::class)
class DefaultWriteupsPanelsComponent(
    componentContext: ComponentContext,
    private val dependencies: PresentationDependencies,
    initialSlug: String? = null,
    private val onExternalUrl: (String) -> Unit = {},
) : WriteupsPanelsComponent, ComponentContext by componentContext {

    private val navigation = PanelsNavigation<
        WriteupsPanelsComponent.WriteupsListConfig,
        WriteupsPanelsComponent.ReadArticleConfig,
        WriteupsPanelsComponent.ArticleExtraConfig,
        >()

    private val outlinesBySlug = MutableValue<Map<String, Pair<List<TocHeading>, List<String>>>>(emptyMap())

    override val panels: Value<
        ChildPanels<
            WriteupsPanelsComponent.WriteupsListConfig,
            WriteupsPanelsComponent.WriteupsListChild,
            WriteupsPanelsComponent.ReadArticleConfig,
            WriteupsPanelsComponent.ReadArticleComponent,
            WriteupsPanelsComponent.ArticleExtraConfig,
            WriteupsPanelsComponent.ArticleExtraChild,
            >,
        > = childPanels(
        source = navigation,
        serializers = Triple(
            WriteupsPanelsComponent.WriteupsListConfig.serializer(),
            WriteupsPanelsComponent.ReadArticleConfig.serializer(),
            WriteupsPanelsComponent.ArticleExtraConfig.serializer(),
        ),
        initialPanels = {
            Panels(
                main = WriteupsPanelsComponent.WriteupsListConfig,
                details = initialSlug?.let { WriteupsPanelsComponent.ReadArticleConfig(it) },
                extra = initialSlug?.let { WriteupsPanelsComponent.ArticleExtraConfig(it) },
            )
        },
        handleBackButton = true,
        mainFactory = { _, childContext ->
            DefaultWriteupsListChild(
                componentContext = childContext,
                dependencies = dependencies,
                initialSlug = initialSlug,
                onOpenWriteup = ::openWriteup,
                onSelectionCleared = ::closeWriteup,
            )
        },
        detailsFactory = { config, childContext ->
            DefaultReadArticleComponent(
                componentContext = childContext,
                slug = config.slug,
                dependencies = dependencies,
                onOutlineUpdated = { updatedSlug, headings, demoIds ->
                    outlinesBySlug.value = outlinesBySlug.value + (updatedSlug to (headings to demoIds))
                },
                onNavigateBack = ::closeWriteup,
                onOpenExternalUrl = onExternalUrl,
            )
        },
        extraFactory = { config, _ ->
            WriteupsPanelsComponent.ArticleExtraChild(
                slug = config.slug,
                tocHeadings = outlinesBySlug.map { map -> map[config.slug]?.first.orEmpty() },
                embeddedDemoIds = outlinesBySlug.map { map -> map[config.slug]?.second.orEmpty() },
            )
        },
    )

    override val webNavigation: WebNavigation<*> = childPanelsWebNavigation(
        navigator = navigation,
        panels = panels,
        serializers = Triple(
            WriteupsPanelsComponent.WriteupsListConfig.serializer(),
            WriteupsPanelsComponent.ReadArticleConfig.serializer(),
            WriteupsPanelsComponent.ArticleExtraConfig.serializer(),
        ),
        pathMapper = { currentPanels ->
            currentPanels.details?.configuration?.slug?.let { "/$it" }
        },
    )

    override fun setMode(mode: ChildPanelsMode) {
        navigation.setMode(mode)
    }

    override fun openWriteup(slug: String) {
        navigation.navigate(
            details = WriteupsPanelsComponent.ReadArticleConfig(slug),
            extra = WriteupsPanelsComponent.ArticleExtraConfig(slug),
        )
    }

    override fun closeWriteup() {
        navigation.navigate(details = null, extra = null)
    }

    override fun setSupportingTocVisible(visible: Boolean) {
        val activeSlug = panels.value.details?.configuration?.slug
        if (visible && activeSlug != null) {
            navigation.activateExtra(WriteupsPanelsComponent.ArticleExtraConfig(activeSlug))
        } else {
            navigation.dismissExtra()
        }
    }
}

// ==================== Resume & Documents ====================

interface ResumeDocsComponent {
    val state: Value<ResumeDocsState>
    val store: Store<ResumeDocsState, ResumeDocsIntent, ResumeDocsAction>
    val downloadEffects: Flow<ResumeDocsAction.TriggerDownload>
    fun onIntent(intent: ResumeDocsIntent)
}

@OptIn(DelicateRetainedApi::class)
class DefaultResumeDocsComponent(
    componentContext: ComponentContext,
    dependencies: PresentationDependencies,
) : ResumeDocsComponent, ComponentContext by componentContext {

    private val _downloadEffects = MutableSharedFlow<ResumeDocsAction.TriggerDownload>(extraBufferCapacity = 4)
    override val downloadEffects: Flow<ResumeDocsAction.TriggerDownload> = _downloadEffects.asSharedFlow()

    override val store: Store<ResumeDocsState, ResumeDocsIntent, ResumeDocsAction> =
        instanceKeeper.retainedStore<ResumeDocsState, ResumeDocsIntent, ResumeDocsAction>(
            initial = ResumeDocsState.Loading,
            scope = instanceKeeper.retainedScope(context = dependencies.mainContext),
        ) {
            configureStore(
                api = dependencies.api,
                analyticsSender = dependencies.analyticsSender,
                platform = dependencies.platform,
                viewportTierProvider = dependencies.viewportTierProvider,
                debuggable = dependencies.debuggable,
            )
        }

    override val state: Value<ResumeDocsState> = store.asDecomposeValue(
        initialState = ResumeDocsState.Loading,
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
        onAction = { action ->
            when (action) {
                is ResumeDocsAction.TriggerDownload -> _downloadEffects.emit(action)
                is ResumeDocsAction.ShowError -> Unit
            }
        },
    )

    override fun onIntent(intent: ResumeDocsIntent) {
        store.intent(intent)
    }
}
