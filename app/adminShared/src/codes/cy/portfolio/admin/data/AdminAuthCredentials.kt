package codes.cy.portfolio.admin.data

import kotlin.concurrent.Volatile

enum class AdminAuthMode(val label: String) {
    DEV_BEARER_TOKEN("Local Dev JWT"),
    OAUTH_SESSION_TOKEN("OAuth Session Token"),
    CLOUDFLARE_ACCESS_ASSERTION("Cloudflare Access JWT"),
}

data class AdminAuthCredentials(
    val mode: AdminAuthMode = AdminAuthMode.DEV_BEARER_TOKEN,
    val token: String = "",
) {
    val sanitizedToken: String
        get() = token.trim()

    val hasToken: Boolean
        get() = sanitizedToken.isNotEmpty()
}

/**
 * Mutable session configuration holder shared between [AdminHttpClientFactory] and auth stores
 * so dynamic admin base URLs and credentials apply immediately without rebuilding HTTP engines.
 */
class AdminSessionConfigHolder(
    initialBaseUrl: String,
    initialCredentials: AdminAuthCredentials = AdminAuthCredentials(),
) {
    @Volatile
    var baseUrl: String = initialBaseUrl.trim().trimEnd('/')
        private set

    @Volatile
    var credentials: AdminAuthCredentials = initialCredentials
        private set

    fun update(
        baseUrl: String,
        credentials: AdminAuthCredentials,
    ) {
        this.baseUrl = baseUrl.trim().trimEnd('/')
        this.credentials = credentials
    }

    fun clearCredentials() {
        this.credentials = credentials.copy(token = "")
    }
}
