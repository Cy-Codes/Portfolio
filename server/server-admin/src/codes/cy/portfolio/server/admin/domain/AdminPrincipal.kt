package codes.cy.portfolio.server.admin.domain

import codes.cy.portfolio.core.model.AdminSessionDto

data class AdminPrincipal(
    val subject: String,
    val role: String,
    val authSource: String,
) {
    fun toSessionDto(): AdminSessionDto =
        AdminSessionDto(
            subject = subject,
            role = role,
            authSource = authSource,
        )
}
