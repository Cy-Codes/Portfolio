package codes.cy.portfolio.presentation.plugins

import codes.cy.portfolio.core.error.ApiError
import kotlin.coroutines.cancellation.CancellationException
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.dsl.StoreBuilder
import pro.respawn.flowmvi.plugins.recover

/**
 * Installs standardized FlowMVI exception recovery preserving coroutine cancellation
 * and surfacing failures as typed [ApiError] instances.
 */
fun <S : MVIState, I : MVIIntent, A : MVIAction> StoreBuilder<S, I, A>.installPortfolioErrorRecovery(
    onError: suspend PipelineContext<S, I, A>.(ApiError) -> Unit,
) {
    recover { cause ->
        if (cause is CancellationException) {
            throw cause
        }
        onError(
            ApiError.InternalError(
                message = cause.message ?: ApiError.GENERIC_INTERNAL_MESSAGE,
            ),
        )
        null
    }
}
