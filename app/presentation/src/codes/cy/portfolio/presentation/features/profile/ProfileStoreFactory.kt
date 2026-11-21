package codes.cy.portfolio.presentation.features.profile

import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.data.AnalyticsEventSender
import codes.cy.portfolio.presentation.data.PublicPortfolioApi
import codes.cy.portfolio.presentation.data.fold
import codes.cy.portfolio.presentation.plugins.installAnalyticsPlugin
import codes.cy.portfolio.presentation.plugins.installPortfolioErrorRecovery
import codes.cy.portfolio.presentation.plugins.installPortfolioLogging
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.StoreBuilder
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

object ProfileStoreFactory {
    fun create(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        debuggable: Boolean = false,
    ): Store<ProfileState, ProfileIntent, ProfileAction> =
        store<ProfileState, ProfileIntent, ProfileAction>(initial = ProfileState.Loading) {
            configureStore(
                api = api,
                analyticsSender = analyticsSender,
                platform = platform,
                viewportTierProvider = viewportTierProvider,
                debuggable = debuggable,
            )
        }

    fun StoreBuilder<ProfileState, ProfileIntent, ProfileAction>.configureStore(
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        debuggable: Boolean = false,
    ) {
        configure {
            name = "ProfileStore"
            this.debuggable = debuggable
            allowTransientSubscriptions = true
        }
        installPortfolioLogging(debuggable = debuggable, tag = "ProfileStore")
        installPortfolioErrorRecovery { apiError ->
            updateState { ProfileState.Error(apiError) }
        }
        installAnalyticsPlugin(
            analyticsSender = analyticsSender,
            platform = platform,
            viewportTierProvider = viewportTierProvider,
        ) { intent, clientPlatform, tier ->
            if (intent is ProfileIntent.Load) {
                TrackEventRequest(
                    eventType = AnalyticsEventType.PAGE_VIEW,
                    path = "/",
                    platform = clientPlatform,
                    viewportTier = tier,
                )
            } else {
                null
            }
        }
        init {
            loadOverview(api = api, isRefresh = false)
        }
        reduce { intent ->
            when (intent) {
                is ProfileIntent.Load -> loadOverview(api = api, isRefresh = false)
                is ProfileIntent.Refresh -> loadOverview(api = api, isRefresh = true)
                is ProfileIntent.SelectSkillCategory -> updateState {
                    if (this is ProfileState.Content) {
                        copy(selectedSkillCategory = intent.category)
                    } else {
                        this
                    }
                }
                is ProfileIntent.OpenProjects -> action(ProfileAction.NavigateToProjects)
                is ProfileIntent.OpenWriteups -> action(ProfileAction.NavigateToWriteups)
                is ProfileIntent.OpenResumeDocs -> action(ProfileAction.NavigateToResumeDocs)
                is ProfileIntent.ClickSocialLink -> action(ProfileAction.OpenExternalUrl(intent.url))
            }
        }
    }

    private suspend fun PipelineContext<ProfileState, ProfileIntent, ProfileAction>.loadOverview(
        api: PublicPortfolioApi,
        isRefresh: Boolean,
    ) {
        updateState {
            when {
                isRefresh && this is ProfileState.Content -> copy(isRefreshing = true)
                else -> ProfileState.Loading
            }
        }
        api.getPortfolioOverview().fold(
            onSuccess = { overview ->
                updateState {
                    val existingCategory = (this as? ProfileState.Content)?.selectedSkillCategory
                    ProfileState.Content(
                        overview = overview,
                        selectedSkillCategory = existingCategory,
                        isRefreshing = false,
                    )
                }
            },
            onFailure = { apiError ->
                updateState { ProfileState.Error(apiError) }
            },
        )
    }
}
