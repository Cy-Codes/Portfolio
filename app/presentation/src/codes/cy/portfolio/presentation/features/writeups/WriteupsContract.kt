package codes.cy.portfolio.presentation.features.writeups

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.presentation.data.toDisplayMessage
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class WriteupsState(
    val items: List<WriteupSummaryDto> = emptyList(),
    val selectedSlug: String? = null,
    val activeTag: String? = null,
    val searchQuery: String = "",
    val page: Int = QueryOptions.DEFAULT_PAGE,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = true,
    val error: ApiError? = null,
) : MVIState {
    val availableTags: List<String>
        get() = items.flatMap { it.tags }.distinct().sorted()

    val displayErrorMessage: String?
        get() = error?.toDisplayMessage()
}

sealed interface WriteupsIntent : MVIIntent {
    data object LoadInitial : WriteupsIntent
    data object Refresh : WriteupsIntent
    data class UpdateSearchQuery(val query: String) : WriteupsIntent
    data object SubmitSearch : WriteupsIntent
    data class SelectTag(val tag: String?) : WriteupsIntent
    data object LoadNextPage : WriteupsIntent
    data class SelectWriteup(val slug: String) : WriteupsIntent
    data object ClearSelection : WriteupsIntent
}

sealed interface WriteupsAction : MVIAction {
    data class OpenWriteup(val slug: String) : WriteupsAction
    data object SelectionCleared : WriteupsAction
    data class ShowError(val message: String) : WriteupsAction
}
