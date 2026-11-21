package codes.cy.portfolio.presentation.features.profile

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.presentation.data.toDisplayMessage
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface ProfileState : MVIState {
    data object Loading : ProfileState

    data class Content(
        val overview: PortfolioOverviewDto,
        val selectedSkillCategory: String? = null,
        val isRefreshing: Boolean = false,
    ) : ProfileState {
        val skillCategories: List<String>
            get() = overview.featuredSkills.map { it.category }.distinct()

        val filteredSkills: List<SkillDto>
            get() = selectedSkillCategory?.let { category ->
                overview.featuredSkills.filter { it.category == category }
            } ?: overview.featuredSkills
    }

    data class Error(
        val error: ApiError,
    ) : ProfileState {
        val displayMessage: String
            get() = error.toDisplayMessage()
    }
}

sealed interface ProfileIntent : MVIIntent {
    data object Load : ProfileIntent
    data object Refresh : ProfileIntent
    data class SelectSkillCategory(val category: String?) : ProfileIntent
    data object OpenProjects : ProfileIntent
    data object OpenWriteups : ProfileIntent
    data object OpenResumeDocs : ProfileIntent
    data class ClickSocialLink(val url: String) : ProfileIntent
}

sealed interface ProfileAction : MVIAction {
    data object NavigateToProjects : ProfileAction
    data object NavigateToWriteups : ProfileAction
    data object NavigateToResumeDocs : ProfileAction
    data class OpenExternalUrl(val url: String) : ProfileAction
}
