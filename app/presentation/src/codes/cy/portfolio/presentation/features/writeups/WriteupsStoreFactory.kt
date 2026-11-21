package codes.cy.portfolio.presentation.features.writeups

import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.SortDirection
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

object WriteupsStoreFactory {
    fun create(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        initialSlug: String? = null,
        debuggable: Boolean = false,
    ): Store<WriteupsState, WriteupsIntent, WriteupsAction> =
        store<WriteupsState, WriteupsIntent, WriteupsAction>(
            initial = WriteupsState(selectedSlug = initialSlug),
        ) {
            configureStore(
                api = api,
                analyticsSender = analyticsSender,
                platform = platform,
                viewportTierProvider = viewportTierProvider,
                debuggable = debuggable,
            )
        }

    fun StoreBuilder<WriteupsState, WriteupsIntent, WriteupsAction>.configureStore(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        debuggable: Boolean = false,
    ) {
        configure {
            name = "WriteupsStore"
            this.debuggable = debuggable
            allowTransientSubscriptions = true
        }
        installPortfolioLogging(debuggable = debuggable, tag = "WriteupsStore")
        installPortfolioErrorRecovery { apiError ->
            updateState { copy(isLoading = false, error = apiError) }
            action(WriteupsAction.ShowError(apiError.toDisplayMessage()))
        }
        installAnalyticsPlugin(
            analyticsSender = analyticsSender,
            platform = platform,
            viewportTierProvider = viewportTierProvider,
        ) { intent, clientPlatform, tier ->
            if (intent is WriteupsIntent.LoadInitial) {
                TrackEventRequest(
                    eventType = AnalyticsEventType.PAGE_VIEW,
                    path = "/writeups",
                    platform = clientPlatform,
                    viewportTier = tier,
                )
            } else {
                null
            }
        }
        init {
            loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)
        }
        reduce { intent ->
            when (intent) {
                is WriteupsIntent.LoadInitial,
                is WriteupsIntent.Refresh -> loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)

                is WriteupsIntent.UpdateSearchQuery -> updateState { copy(searchQuery = intent.query) }

                is WriteupsIntent.SubmitSearch ->
                    loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)

                is WriteupsIntent.SelectTag -> {
                    val normalizedTag = intent.tag?.trim()?.takeIf { it.isNotEmpty() }
                    updateState { copy(activeTag = normalizedTag) }
                    loadPage(api = api, targetPage = QueryOptions.DEFAULT_PAGE, append = false)
                }

                is WriteupsIntent.LoadNextPage -> withState {
                    if (hasNextPage && !isLoading) {
                        loadPage(api = api, targetPage = page + 1, append = true)
                    }
                }

                is WriteupsIntent.SelectWriteup -> {
                    updateState { copy(selectedSlug = intent.slug) }
                    action(WriteupsAction.OpenWriteup(intent.slug))
                }

                is WriteupsIntent.ClearSelection -> {
                    updateState { copy(selectedSlug = null) }
                    action(WriteupsAction.SelectionCleared)
                }
            }
        }
    }

    private suspend fun PipelineContext<WriteupsState, WriteupsIntent, WriteupsAction>.loadPage(
        api: PublicPortfolioApi,
        targetPage: Int,
        append: Boolean,
    ) {
        var tagFilter: String? = null
        var queryFilter: String? = null
        updateState {
            tagFilter = activeTag
            queryFilter = searchQuery.trim().takeIf { it.isNotEmpty() }
            copy(isLoading = true, error = null)
        }
        api.getWriteups(
            options = QueryOptions(
                page = targetPage,
                pageSize = QueryOptions.DEFAULT_PAGE_SIZE,
                sortBy = "published_at",
                sortDirection = SortDirection.DESC,
            ),
            tag = tagFilter,
            searchQuery = queryFilter,
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
                        isLoading = false,
                        error = null,
                    )
                }
            },
            onFailure = { apiError ->
                updateState { copy(isLoading = false, error = apiError) }
                action(WriteupsAction.ShowError(apiError.toDisplayMessage()))
            },
        )
    }
}
