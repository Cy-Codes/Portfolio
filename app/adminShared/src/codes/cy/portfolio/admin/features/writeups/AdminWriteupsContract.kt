package codes.cy.portfolio.admin.features.writeups

import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.QueryOptions
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class WriteupFormDraft(
    val editingId: String? = null,
    val slug: String = "",
    val title: String = "",
    val subtitle: String = "",
    val summary: String = "",
    val bodyMarkdown: String = "",
    val tagsInput: String = "",
    val status: WriteupStatus = WriteupStatus.DRAFT,
    val readingTimeMinInput: String = "3",
    val coverDocumentId: String? = null,
) {
    val parsedTags: List<String>
        get() = tagsInput.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

data class AdminWriteupsState(
    val items: List<WriteupSummaryDto> = emptyList(),
    val statusFilter: WriteupStatus? = null,
    val page: Int = QueryOptions.DEFAULT_PAGE,
    val totalPages: Int = 0,
    val totalCount: Long = 0L,
    val availableDocuments: List<DocumentDto> = emptyList(),
    val draft: WriteupFormDraft = WriteupFormDraft(),
    val isLoadingList: Boolean = true,
    val isLoadingDetail: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) : MVIState {
    val filteredItems: List<WriteupSummaryDto>
        get() = statusFilter?.let { filter -> items.filter { it.status == filter } } ?: items
}

sealed interface AdminWriteupsIntent : MVIIntent {
    data object Load : AdminWriteupsIntent
    data class GoToPage(val page: Int) : AdminWriteupsIntent
    data class FilterByStatus(val status: WriteupStatus?) : AdminWriteupsIntent
    data class SelectWriteup(val id: String) : AdminWriteupsIntent
    data object NewWriteup : AdminWriteupsIntent
    data class UpdateDraft(val draft: WriteupFormDraft) : AdminWriteupsIntent
    data object AutoSlugFromTitle : AdminWriteupsIntent
    data object AutoEstimateReadingTime : AdminWriteupsIntent
    data class InsertDemoSnippet(val demoId: String) : AdminWriteupsIntent
    data class SaveWithStatus(val targetStatus: WriteupStatus) : AdminWriteupsIntent
    data class DeleteWriteup(val id: String) : AdminWriteupsIntent
    data object DismissFeedback : AdminWriteupsIntent
}

sealed interface AdminWriteupsAction : MVIAction

fun WriteupDetailDto.toFormDraft(): WriteupFormDraft =
    WriteupFormDraft(
        editingId = id,
        slug = slug,
        title = title,
        subtitle = subtitle.orEmpty(),
        summary = summary,
        bodyMarkdown = bodyMarkdown,
        tagsInput = tags.joinToString(", "),
        status = status,
        readingTimeMinInput = readingTimeMin.toString(),
        coverDocumentId = coverDocumentId,
    )
