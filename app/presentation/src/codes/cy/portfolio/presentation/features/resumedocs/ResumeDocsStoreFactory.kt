package codes.cy.portfolio.presentation.features.resumedocs

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

object ResumeDocsStoreFactory {
    fun create(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        debuggable: Boolean = false,
    ): Store<ResumeDocsState, ResumeDocsIntent, ResumeDocsAction> =
        store<ResumeDocsState, ResumeDocsIntent, ResumeDocsAction>(
            initial = ResumeDocsState.Loading,
        ) {
            configureStore(
                api = api,
                analyticsSender = analyticsSender,
                platform = platform,
                viewportTierProvider = viewportTierProvider,
                debuggable = debuggable,
            )
        }

    fun StoreBuilder<ResumeDocsState, ResumeDocsIntent, ResumeDocsAction>.configureStore(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        debuggable: Boolean = false,
    ) {
        configure {
            name = "ResumeDocsStore"
            this.debuggable = debuggable
            allowTransientSubscriptions = true
        }
        installPortfolioLogging(debuggable = debuggable, tag = "ResumeDocsStore")
        installPortfolioErrorRecovery { apiError ->
            updateState { ResumeDocsState.Error(apiError) }
            action(ResumeDocsAction.ShowError(apiError.toDisplayMessage()))
        }
        installAnalyticsPlugin(
            analyticsSender = analyticsSender,
            platform = platform,
            viewportTierProvider = viewportTierProvider,
        ) { intent, clientPlatform, tier ->
            when (intent) {
                is ResumeDocsIntent.Load -> TrackEventRequest(
                    eventType = AnalyticsEventType.PAGE_VIEW,
                    path = "/resume",
                    platform = clientPlatform,
                    viewportTier = tier,
                )
                is ResumeDocsIntent.RequestDownload -> TrackEventRequest(
                    eventType = AnalyticsEventType.DOCUMENT_DOWNLOAD,
                    path = "/api/v1/public/documents/${intent.document.id}/download",
                    entityId = intent.document.id,
                    platform = clientPlatform,
                    viewportTier = tier,
                )
                else -> null
            }
        }
        init {
            loadDocuments(api = api, isRefresh = false)
        }
        reduce { intent ->
            when (intent) {
                is ResumeDocsIntent.Load -> loadDocuments(api = api, isRefresh = false)
                is ResumeDocsIntent.Refresh -> loadDocuments(api = api, isRefresh = true)
                is ResumeDocsIntent.SelectCategory -> updateState {
                    if (this is ResumeDocsState.Content) {
                        copy(selectedCategory = intent.category)
                    } else {
                        this
                    }
                }
                is ResumeDocsIntent.RequestDownload -> {
                    val downloadUrl = api.buildDocumentDownloadUrl(intent.document.id)
                    action(
                        ResumeDocsAction.TriggerDownload(
                            url = downloadUrl,
                            filename = intent.document.originalFilename,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun PipelineContext<ResumeDocsState, ResumeDocsIntent, ResumeDocsAction>.loadDocuments(
        api: PublicPortfolioApi,
        isRefresh: Boolean,
    ) {
        updateState {
            when {
                isRefresh && this is ResumeDocsState.Content -> copy(isRefreshing = true)
                else -> ResumeDocsState.Loading
            }
        }
        api.getDocuments(
            options = QueryOptions(
                page = QueryOptions.DEFAULT_PAGE,
                pageSize = QueryOptions.MAX_PAGE_SIZE,
            ),
        ).fold(
            onSuccess = { paged ->
                updateState {
                    val existingCategory = (this as? ResumeDocsState.Content)?.selectedCategory
                    ResumeDocsState.Content(
                        documents = paged.items,
                        selectedCategory = existingCategory,
                        isRefreshing = false,
                    )
                }
            },
            onFailure = { apiError ->
                updateState { ResumeDocsState.Error(apiError) }
                action(ResumeDocsAction.ShowError(apiError.toDisplayMessage()))
            },
        )
    }
}
