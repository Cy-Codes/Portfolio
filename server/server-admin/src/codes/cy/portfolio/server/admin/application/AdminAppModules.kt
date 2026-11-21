package codes.cy.portfolio.server.admin.application

import codes.cy.portfolio.server.admin.domain.AdminServerConfig
import codes.cy.portfolio.server.admin.infrastructure.AdminTokenAuthenticator

data class AdminAppModules(
    val config: AdminServerConfig,
    val authenticator: AdminTokenAuthenticator,
    val readinessChecker: ReadinessChecker,
    val queryService: AdminPortfolioQueryService,
    val commandService: AdminPortfolioCommandService,
    val documentService: AdminDocumentCommandService,
    val statsService: AdminStatsQueryService,
)
