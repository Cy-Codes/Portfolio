package codes.cy.portfolio.admin.features.auth

import codes.cy.portfolio.admin.data.AdminAuthMode
import codes.cy.portfolio.core.model.AdminSessionDto
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class AdminAuthState(
    val baseUrl: String,
    val mode: AdminAuthMode = AdminAuthMode.DEV_BEARER_TOKEN,
    val tokenInput: String = "",
    val isAuthenticating: Boolean = false,
    val session: AdminSessionDto? = null,
    val errorMessage: String? = null,
) : MVIState {
    val isAuthenticated: Boolean
        get() = session != null
}

sealed interface AdminAuthIntent : MVIIntent {
    data class BaseUrlChanged(val baseUrl: String) : AdminAuthIntent
    data class ModeChanged(val mode: AdminAuthMode) : AdminAuthIntent
    data class TokenChanged(val token: String) : AdminAuthIntent
    data object SignIn : AdminAuthIntent
    data object SignOut : AdminAuthIntent
    data object ClearError : AdminAuthIntent
}

sealed interface AdminAuthAction : MVIAction {
    data class Authenticated(val session: AdminSessionDto) : AdminAuthAction
    data object SignedOut : AdminAuthAction
}
