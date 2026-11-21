package codes.cy.portfolio.presentation.data

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
 * Configures Ktor Client with shared [PortfolioJson], `@Resource` routing, and
 * non-throwing HTTP status handling for exhaustive [codes.cy.portfolio.core.error.ApiError] decoding.
 */
object PortfolioHttpClientFactory {
    fun create(
        baseUrl: String,
        engine: HttpClientEngine? = null,
    ): HttpClient =
        if (engine != null) {
            HttpClient(engine) { applyDefaults(baseUrl) }
        } else {
            createPlatformHttpClient { applyDefaults(baseUrl) }
        }

    private fun HttpClientConfig<*>.applyDefaults(baseUrl: String) {
        expectSuccess = false
        install(Resources)
        install(ContentNegotiation) {
            json(PortfolioJson)
        }
        defaultRequest {
            val normalizedBase = baseUrl.trimEnd('/')
            if (normalizedBase.isNotEmpty()) {
                url("$normalizedBase/")
            }
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }
}
