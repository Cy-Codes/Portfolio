package codes.cy.portfolio.presentation.features.projects

import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.presentation.data.AnalyticsEventSender
import codes.cy.portfolio.presentation.data.PublicPortfolioApi
import codes.cy.portfolio.presentation.data.fold
import codes.cy.portfolio.presentation.data.toDisplayMessage
import codes.cy.portfolio.presentation.plugins.installAnalyticsPlugin
import codes.cy.portfolio.presentation.plugins.installPortfolioErrorRecovery
import codes.cy.portfolio.presentation.plugins.installPortfolioLogging
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.StoreBuilder
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

object ProjectsStoreFactory {
    fun create(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        initialSlug: String? = null,
        debuggable: Boolean = false,
    ): Store<ProjectsState, ProjectsIntent, ProjectsAction> =
        store<ProjectsState, ProjectsIntent, ProjectsAction>(
            initial = ProjectsState(selectedSlug = initialSlug),
        ) {
            configureStore(
                api = api,
                analyticsSender = analyticsSender,
                platform = platform,
                viewportTierProvider = viewportTierProvider,
                initialSlug = initialSlug,
                debuggable = debuggable,
            )
        }

    fun StoreBuilder<ProjectsState, ProjectsIntent, ProjectsAction>.configureStore(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        initialSlug: String? = null,
        debuggable: Boolean = false,
    ) {
        configure {
            name = "ProjectsStore"
            this.debuggable = debuggable
            allowTransientSubscriptions = true
        }
        installPortfolioLogging(debuggable = debuggable, tag = "ProjectsStore")
        installPortfolioErrorRecovery { apiError ->
            updateState { copy(isLoadingList = false, isLoadingDetail = false, error = apiError) }
            action(ProjectsAction.ShowError(apiError.toDisplayMessage()))
        }
        installAnalyticsPlugin(
            analyticsSender = analyticsSender,
            platform = platform,
            viewportTierProvider = viewportTierProvider,
        ) { intent, clientPlatform, tier ->
            when (intent) {
                is ProjectsIntent.SelectProject -> TrackEventRequest(
                    eventType = AnalyticsEventType.PROJECT_CLICK,
                    path = "/projects/${intent.slug}",
                    platform = clientPlatform,
                    viewportTier = tier,
                )
                is ProjectsIntent.LoadInitial -> TrackEventRequest(
                    eventType = AnalyticsEventType.PAGE_VIEW,
                    path = "/projects",
                    platform = clientPlatform,
                    viewportTier = tier,
                )
                else -> null
            }
        }
        init {
            loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)
            if (!initialSlug.isNullOrBlank()) {
                loadProjectDetail(api = api, slug = initialSlug)
            }
        }
        reduce { intent ->
            when (intent) {
                is ProjectsIntent.LoadInitial,
                is ProjectsIntent.Refresh -> loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)

                is ProjectsIntent.ToggleFeaturedOnly -> {
                    updateState { copy(featuredOnly = intent.featuredOnly) }
                    loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)
                }

                is ProjectsIntent.FilterBySkill -> {
                    val normalizedSkill = intent.skill?.trim()?.takeIf { it.isNotEmpty() }
                    updateState { copy(selectedSkill = normalizedSkill) }
                    loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)
                }

                is ProjectsIntent.LoadNextPage -> withState {
                    if (hasNextPage && !isLoadingList) {
                        loadPage(api = api, targetPage = page + 1, append = true)
                    }
                }

                is ProjectsIntent.SelectProject -> {
                    loadProjectDetail(api = api, slug = intent.slug)
                    action(ProjectsAction.ProjectSelected(intent.slug))
                }

                is ProjectsIntent.ClearSelection -> {
                    updateState { copy(selectedSlug = null, selectedProject = null, isLoadingDetail = false) }
                    action(ProjectsAction.SelectionCleared)
                }

                is ProjectsIntent.OpenExternalLink -> action(ProjectsAction.OpenExternalUrl(intent.url))
            }
        }
    }

    private suspend fun PipelineContext<ProjectsState, ProjectsIntent, ProjectsAction>.loadPage(
        api: PublicPortfolioApi,
        targetPage: Int,
        append: Boolean,
    ) {
        var featuredFilter = false
        var skillFilter: String? = null
        updateState {
            featuredFilter = featuredOnly
            skillFilter = selectedSkill
            copy(isLoadingList = true, error = null)
        }
        api.getProjects(
            options = QueryOptions(page = targetPage, pageSize = QueryOptions.DEFAULT_PAGE_SIZE),
            featuredOnly = if (featuredFilter) true else null,
            skillName = skillFilter,
        ).fold(
            onSuccess = { paged ->
                updateState {
                    val mergedItems = if (append) {
                        (items + paged.items).distinctBy { it.id }
                    } else {
                        paged.items
                    }
                    copy(
                        items = mergedItems,
                        page = paged.page,
                        totalPages = paged.totalPages,
                        hasNextPage = paged.hasNextPage,
                        isLoadingList = false,
                        error = null,
                    )
                }
            },
            onFailure = { apiError ->
                updateState { copy(isLoadingList = false, error = apiError) }
                action(ProjectsAction.ShowError(apiError.toDisplayMessage()))
            },
        )
    }

    private suspend fun PipelineContext<ProjectsState, ProjectsIntent, ProjectsAction>.loadProjectDetail(
        api: PublicPortfolioApi,
        slug: String,
    ) {
        updateState {
            copy(selectedSlug = slug, isLoadingDetail = true, error = null)
        }
        api.getProjectBySlug(slug).fold(
            onSuccess = { detail ->
                updateState {
                    copy(
                        selectedSlug = detail.slug,
                        selectedProject = detail,
                        isLoadingDetail = false,
                        error = null,
                    )
                }
            },
            onFailure = { apiError ->
                updateState { copy(isLoadingDetail = false, error = apiError) }
                action(ProjectsAction.ShowError(apiError.toDisplayMessage()))
            },
        )
    }
}
