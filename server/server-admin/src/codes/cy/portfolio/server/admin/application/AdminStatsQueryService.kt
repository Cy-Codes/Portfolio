package codes.cy.portfolio.server.admin.application

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.StatsOverviewDto
import codes.cy.portfolio.core.model.StatsTimeRange
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.repositories.AnalyticsQueryRepository

class AdminStatsQueryService(
    private val analyticsRepo: AnalyticsQueryRepository,
) {
    suspend fun getOverview(rawRange: String?): StatsOverviewDto {
        val range = runCatching { StatsTimeRange.parse(rawRange) }.getOrElse { ex ->
            throw ValidationException(
                message = ex.message ?: "Invalid stats range '$rawRange'.",
                fieldErrors = listOf(FieldViolation("range", "Expected one of: 7d, 30d, 90d, all.")),
            )
        }
        return analyticsRepo.getStatsOverview(range)
    }
}
