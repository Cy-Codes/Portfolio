package codes.cy.portfolio.presentation.plugins

import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.dsl.StoreBuilder
import pro.respawn.flowmvi.plugins.enableLogging

fun <S : MVIState, I : MVIIntent, A : MVIAction> StoreBuilder<S, I, A>.installPortfolioLogging(
    debuggable: Boolean,
    tag: String,
) {
    if (debuggable) {
        enableLogging(tag = tag)
    }
}
