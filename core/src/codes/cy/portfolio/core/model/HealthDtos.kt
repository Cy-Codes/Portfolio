package codes.cy.portfolio.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthStatusDto(
    val status: String,
    val service: String,
    val databaseReady: Boolean? = null,
    val appliedMigrationCount: Int? = null,
)

@Serializable
data class AdminSessionDto(
    val subject: String,
    val role: String,
    val authSource: String,
)
