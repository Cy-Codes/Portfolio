package codes.cy.portfolio.admin.features.stats

import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.fold
import codes.cy.portfolio.admin.data.toAdminDisplayMessage
import codes.cy.portfolio.core.model.StatsTimeRange
import kotlin.coroutines.cancellation.CancellationException
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.recover
import pro.respawn.flowmvi.plugins.reduce

object AdminStatsStoreFactory {
    fun create(
        queryApi: AdminQueryApi,
    ): Store<AdminStatsState, AdminStatsIntent, AdminStatsAction> =
        store(initial = AdminStatsState()) {
            recover { cause ->
                if (cause is CancellationException) throw cause
                updateState {
                    copy(
                        isLoading = false,
                        errorMessage = cause.message ?: "Failed to load analytics overview.",
                    )
                }
                null
            }

            init {
                loadRange(queryApi, StatsTimeRange.THIRTY_DAYS)
            }

            reduce { intent ->
                when (intent) {
                    is AdminStatsIntent.Load,
                    is AdminStatsIntent.Refresh,
                    -> {
                        var currentRange = StatsTimeRange.THIRTY_DAYS
                        updateState {
                            currentRange = selectedRange
                            copy(isLoading = true, errorMessage = null)
                        }
                        loadRange(queryApi, currentRange)
                    }

                    is AdminStatsIntent.SelectRange -> {
                        updateState {
                            copy(selectedRange = intent.range, isLoading = true, errorMessage = null)
                        }
                        loadRange(queryApi, intent.range)
                    }
                }
            }
        }

    private suspend fun PipelineContext<AdminStatsState, AdminStatsIntent, AdminStatsAction>.loadRange(
        queryApi: AdminQueryApi,
        range: StatsTimeRange,
    ) {
        queryApi.getStatsOverview(range).fold(
            onSuccess = { dto ->
                updateState {
                    copy(
                        selectedRange = dto.range,
                        overview = dto,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            },
            onFailure = { error ->
                updateState {
                    copy(
                        isLoading = false,
                        errorMessage = error.toAdminDisplayMessage(),
                    )
                }
            },
        )
    }
}
