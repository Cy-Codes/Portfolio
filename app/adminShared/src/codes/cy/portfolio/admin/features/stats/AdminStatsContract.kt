package codes.cy.portfolio.admin.features.stats

import codes.cy.portfolio.core.model.StatsOverviewDto
import codes.cy.portfolio.core.model.StatsTimeRange
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class AdminStatsState(
    val selectedRange: StatsTimeRange = StatsTimeRange.THIRTY_DAYS,
    val overview: StatsOverviewDto? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) : MVIState

sealed interface AdminStatsIntent : MVIIntent {
    data object Load : AdminStatsIntent
    data class SelectRange(val range: StatsTimeRange) : AdminStatsIntent
    data object Refresh : AdminStatsIntent
}

sealed interface AdminStatsAction : MVIAction
