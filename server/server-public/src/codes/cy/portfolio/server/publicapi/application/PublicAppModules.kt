package codes.cy.portfolio.server.publicapi.application

import codes.cy.portfolio.server.publicapi.domain.PublicServerConfig

data class PublicAppModules(
    val config: PublicServerConfig,
    val readinessChecker: ReadinessChecker,
    val queryService: PublicPortfolioQueryService,
    val analyticsService: PublicAnalyticsCommandService,
)
