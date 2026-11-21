package codes.cy.portfolio.server.publicapi.application

import codes.cy.portfolio.server.db.config.SchemaReadinessStatus

fun interface ReadinessChecker {
    suspend fun check(): SchemaReadinessStatus
}
