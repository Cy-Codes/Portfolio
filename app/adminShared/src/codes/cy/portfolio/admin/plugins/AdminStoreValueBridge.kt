package codes.cy.portfolio.admin.plugins

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.repeatOnLifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.collect

/**
 * Single-subscription FlowMVI -> Decompose [Value] bridge bound explicitly to [coroutineContext].
 */
fun <S : MVIState, I : MVIIntent, A : MVIAction> Store<S, I, A>.asAdminDecomposeValue(
    initialState: S,
    lifecycle: Lifecycle,
    coroutineContext: CoroutineContext,
    onAction: (suspend (A) -> Unit)? = null,
): Value<S> {
    val mutableValue = MutableValue(initialState)
    val scope = CoroutineScope(context = coroutineContext).withLifecycle(lifecycle)
    scope.launch {
        lifecycle.repeatOnLifecycle(
            minActiveState = Lifecycle.State.STARTED,
            context = coroutineContext,
        ) {
            this@asAdminDecomposeValue.collect {
                coroutineScope {
                    launch {
                        states.collect { nextState ->
                            mutableValue.value = nextState
                        }
                    }
                    if (onAction != null) {
                        launch {
                            actions.collect { action ->
                                onAction(action)
                            }
                        }
                    }
                }
            }
        }
    }
    return mutableValue
}
