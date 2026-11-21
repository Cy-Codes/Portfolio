package codes.cy.portfolio.presentation.plugins

import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.data.AnalyticsEventSender
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.dsl.StoreBuilder
import pro.respawn.flowmvi.dsl.plugin

/**
 * Reusable FlowMVI plugin dispatching non-blocking cookieless telemetry events
 * via `POST /api/v1/public/events` without mutating query handlers.
 */
fun <S : MVIState, I : MVIIntent, A : MVIAction> StoreBuilder<S, I, A>.installAnalyticsPlugin(
    analyticsSender: AnalyticsEventSender,
    platform: ClientPlatform,
    viewportTierProvider: () -> ViewportTier,
    mapIntentToEvent: (intent: I, platform: ClientPlatform, viewportTier: ViewportTier) -> TrackEventRequest?,
) {
    install(
        plugin {
            name = "PortfolioAnalyticsPlugin"
            onIntent { intent ->
                val request = mapIntentToEvent(intent, platform, viewportTierProvider())
                if (request != null) {
                    launch {
                        runCatching { analyticsSender.recordEvent(request) }
                    }
                }
                intent
            }
        },
    )
}
