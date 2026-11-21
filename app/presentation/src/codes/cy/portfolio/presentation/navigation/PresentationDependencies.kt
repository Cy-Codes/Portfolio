package codes.cy.portfolio.presentation.navigation

import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.data.AnalyticsEventSender
import codes.cy.portfolio.presentation.data.KtorPublicPortfolioApi
import codes.cy.portfolio.presentation.data.PortfolioHttpClientFactory
import codes.cy.portfolio.presentation.data.PublicPortfolioApi
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

data class PresentationDependencies(
    val api: PublicPortfolioApi,
    val analyticsSender: AnalyticsEventSender,
    val platform: ClientPlatform,
    val viewportTierProvider: () -> ViewportTier,
    val mainContext: CoroutineContext = Dispatchers.Main.immediate,
    val debuggable: Boolean = false,
) {
    companion object {
        fun fromBaseUrl(
            baseUrl: String,
            platform: ClientPlatform,
            viewportTierProvider: () -> ViewportTier,
            mainContext: CoroutineContext = Dispatchers.Main.immediate,
            debuggable: Boolean = false,
        ): PresentationDependencies {
            val client = PortfolioHttpClientFactory.create(baseUrl = baseUrl)
            val api = KtorPublicPortfolioApi(httpClient = client, baseUrl = baseUrl)
            return PresentationDependencies(
                api = api,
                analyticsSender = api,
                platform = platform,
                viewportTierProvider = viewportTierProvider,
                mainContext = mainContext,
                debuggable = debuggable,
            )
        }
    }
}
