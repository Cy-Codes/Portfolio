package codes.cy.portfolio.admin.data

import codes.cy.portfolio.admin.platform.createAdminPlatformHttpClient
import codes.cy.portfolio.core.serialization.PortfolioJson
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

/**
 * Creates separate HTTP clients for authenticated admin JSON calls and unauthenticated
 * direct-to-GCS/MinIO presigned binary PUT uploads (preventing auth header leakage).
 */
object AdminHttpClientFactory {
    private const val BEARER_PREFIX: String = "Bearer "

    fun createApiClient(
        configHolder: AdminSessionConfigHolder,
        engine: HttpClientEngine? = null,
    ): HttpClient =
        buildClient(engine) {
            expectSuccess = false
            install(Resources)
            install(ContentNegotiation) {
                json(PortfolioJson)
            }
            defaultRequest {
                val normalizedBase = configHolder.baseUrl.trimEnd('/')
                if (normalizedBase.isNotEmpty()) {
                    url("$normalizedBase/")
                }
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                val credentials = configHolder.credentials
                if (credentials.hasToken) {
                    val token = credentials.sanitizedToken
                    when (credentials.mode) {
                        AdminAuthMode.DEV_BEARER_TOKEN,
                        AdminAuthMode.OAUTH_SESSION_TOKEN,
                        AdminAuthMode.OIDC_ID_TOKEN,
                        -> header(HttpHeaders.Authorization, "$BEARER_PREFIX$token")
                    }
                }
            }
        }

    fun createBinaryUploadClient(
        engine: HttpClientEngine? = null,
    ): HttpClient =
        buildClient(engine) {
            expectSuccess = false
        }

    private fun buildClient(
        engine: HttpClientEngine?,
        configure: HttpClientConfig<*>.() -> Unit,
    ): HttpClient =
        if (engine != null) {
            HttpClient(engine, configure)
        } else {
            createAdminPlatformHttpClient(configure)
        }
}
