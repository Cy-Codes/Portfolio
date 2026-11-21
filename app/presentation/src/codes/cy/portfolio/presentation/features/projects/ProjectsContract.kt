package codes.cy.portfolio.presentation.features.projects

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.presentation.data.toDisplayMessage
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class ProjectsState(
    val items: List<ProjectSummaryDto> = emptyList(),
    val selectedSlug: String? = null,
    val selectedProject: ProjectDetailDto? = null,
    val page: Int = QueryOptions.DEFAULT_PAGE,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
    val featuredOnly: Boolean = false,
    val selectedSkill: String? = null,
    val isLoadingList: Boolean = true,
    val isLoadingDetail: Boolean = false,
    val error: ApiError? = null,
) : MVIState {
    val displayErrorMessage: String?
        get() = error?.toDisplayMessage()
}

sealed interface ProjectsIntent : MVIIntent {
    data object LoadInitial : ProjectsIntent
    data object Refresh : ProjectsIntent
    data class ToggleFeaturedOnly(val featuredOnly: Boolean) : ProjectsIntent
    data class FilterBySkill(val skill: String?) : ProjectsIntent
    data object LoadNextPage : ProjectsIntent
    data class SelectProject(val slug: String) : ProjectsIntent
    data object ClearSelection : ProjectsIntent
    data class OpenExternalLink(val url: String) : ProjectsIntent
}

sealed interface ProjectsAction : MVIAction {
    data class ProjectSelected(val slug: String) : ProjectsAction
    data object SelectionCleared : ProjectsAction
    data class OpenExternalUrl(val url: String) : ProjectsAction
    data class ShowError(val message: String) : ProjectsAction
}
