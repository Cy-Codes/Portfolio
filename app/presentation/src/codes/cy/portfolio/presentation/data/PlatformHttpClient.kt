package codes.cy.portfolio.presentation.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

expect fun createPlatformHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient
