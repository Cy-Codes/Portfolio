package codes.cy.portfolio.admin.features.auth

import codes.cy.portfolio.admin.data.AdminAuthCredentials
import codes.cy.portfolio.admin.data.AdminAuthMode
import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.AdminSessionConfigHolder
import codes.cy.portfolio.admin.data.fold
import codes.cy.portfolio.admin.data.toAdminDisplayMessage
import kotlin.coroutines.cancellation.CancellationException
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.recover
import pro.respawn.flowmvi.plugins.reduce

object AdminAuthStoreFactory {
    fun create(
        queryApi: AdminQueryApi,
        configHolder: AdminSessionConfigHolder,
        initialMode: AdminAuthMode = AdminAuthMode.DEV_BEARER_TOKEN,
        initialToken: String = "",
        autoVerifyInitialToken: Boolean = initialToken.isNotBlank(),
    ): Store<AdminAuthState, AdminAuthIntent, AdminAuthAction> =
        store(
            initial = AdminAuthState(
                baseUrl = configHolder.baseUrl,
                mode = initialMode,
                tokenInput = initialToken,
            ),
        ) {
            recover { cause ->
                if (cause is CancellationException) throw cause
                updateState {
                    copy(
                        isAuthenticating = false,
                        errorMessage = cause.message ?: "Authentication verification failed.",
                    )
                }
                null
            }

            if (autoVerifyInitialToken && initialToken.isNotBlank()) {
                init {
                    intent(AdminAuthIntent.SignIn)
                }
            }

            reduce { intent ->
                when (intent) {
                    is AdminAuthIntent.BaseUrlChanged -> updateState {
                        copy(baseUrl = intent.baseUrl, errorMessage = null)
                    }

                    is AdminAuthIntent.ModeChanged -> updateState {
                        copy(mode = intent.mode, errorMessage = null)
                    }

                    is AdminAuthIntent.TokenChanged -> updateState {
                        copy(tokenInput = intent.token, errorMessage = null)
                    }

                    is AdminAuthIntent.ClearError -> updateState {
                        copy(errorMessage = null)
                    }

                    is AdminAuthIntent.SignOut -> {
                        configHolder.clearCredentials()
                        updateState {
                            copy(
                                isAuthenticating = false,
                                session = null,
                                errorMessage = null,
                            )
                        }
                        action(AdminAuthAction.SignedOut)
                    }

                    is AdminAuthIntent.SignIn -> {
                        var trimmedUrl = ""
                        var trimmedToken = ""
                        var currentMode = AdminAuthMode.DEV_BEARER_TOKEN
                        updateState {
                            trimmedUrl = baseUrl.trim().trimEnd('/')
                            trimmedToken = tokenInput.trim()
                            currentMode = mode
                            when {
                                trimmedUrl.isEmpty() -> copy(errorMessage = "Admin API base URL must not be blank.")
                                trimmedToken.isEmpty() -> copy(errorMessage = "Paste a bearer JWT or OIDC ID token.")
                                else -> copy(
                                    baseUrl = trimmedUrl,
                                    isAuthenticating = true,
                                    errorMessage = null,
                                )
                            }
                        }
                        if (trimmedUrl.isEmpty() || trimmedToken.isEmpty()) {
                            return@reduce
                        }

                        configHolder.update(
                            baseUrl = trimmedUrl,
                            credentials = AdminAuthCredentials(mode = currentMode, token = trimmedToken),
                        )

                        queryApi.getSession().fold(
                            onSuccess = { session ->
                                updateState {
                                    copy(
                                        isAuthenticating = false,
                                        session = session,
                                        errorMessage = null,
                                    )
                                }
                                action(AdminAuthAction.Authenticated(session))
                            },
                            onFailure = { error ->
                                configHolder.clearCredentials()
                                updateState {
                                    copy(
                                        isAuthenticating = false,
                                        session = null,
                                        errorMessage = error.toAdminDisplayMessage(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
}
