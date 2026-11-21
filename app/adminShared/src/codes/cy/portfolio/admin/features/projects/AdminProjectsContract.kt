package codes.cy.portfolio.admin.features.projects

import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.pagination.QueryOptions
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class ProjectFormDraft(
    val editingId: String? = null,
    val slug: String = "",
    val title: String = "",
    val summary: String = "",
    val caseStudyMarkdown: String = "",
    val repoUrl: String = "",
    val liveUrl: String = "",
    val coverDocumentId: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val isFeatured: Boolean = false,
    val selectedSkillIds: Set<String> = emptySet(),
    val sortOrderInput: String = "0",
)

data class AdminProjectsState(
    val items: List<ProjectSummaryDto> = emptyList(),
    val page: Int = QueryOptions.DEFAULT_PAGE,
    val totalPages: Int = 0,
    val totalCount: Long = 0L,
    val availableSkills: List<SkillDto> = emptyList(),
    val availableDocuments: List<DocumentDto> = emptyList(),
    val draft: ProjectFormDraft = ProjectFormDraft(),
    val isLoadingList: Boolean = true,
    val isLoadingDetail: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) : MVIState

sealed interface AdminProjectsIntent : MVIIntent {
    data object Load : AdminProjectsIntent
    data class GoToPage(val page: Int) : AdminProjectsIntent
    data class SelectProject(val id: String) : AdminProjectsIntent
    data object NewProject : AdminProjectsIntent
    data class UpdateDraft(val draft: ProjectFormDraft) : AdminProjectsIntent
    data object GenerateSlugFromTitle : AdminProjectsIntent
    data class ToggleSkill(val skillId: String) : AdminProjectsIntent
    data object SaveProject : AdminProjectsIntent
    data class DeleteProject(val id: String) : AdminProjectsIntent
    data object DismissFeedback : AdminProjectsIntent
}

sealed interface AdminProjectsAction : MVIAction

fun ProjectDetailDto.toFormDraft(): ProjectFormDraft =
    ProjectFormDraft(
        editingId = id,
        slug = slug,
        title = title,
        summary = summary,
        caseStudyMarkdown = caseStudyMarkdown,
        repoUrl = repoUrl.orEmpty(),
        liveUrl = liveUrl.orEmpty(),
        coverDocumentId = coverDocumentId,
        status = status,
        isFeatured = isFeatured,
        selectedSkillIds = skills.map { it.id }.toSet(),
        sortOrderInput = sortOrder.toString(),
    )
