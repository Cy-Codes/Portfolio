package codes.cy.portfolio.presentation.features.resumedocs

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.presentation.data.toDisplayMessage
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface ResumeDocsState : MVIState {
    data object Loading : ResumeDocsState

    data class Content(
        val documents: List<DocumentDto>,
        val selectedCategory: DocumentCategory? = null,
        val isRefreshing: Boolean = false,
    ) : ResumeDocsState {
        val availableCategories: List<DocumentCategory>
            get() = documents.map { it.category }.distinct().sortedBy { it.ordinal }

        val filteredDocuments: List<DocumentDto>
            get() = selectedCategory?.let { category ->
                documents.filter { it.category == category }
            } ?: documents
    }

    data class Error(
        val error: ApiError,
    ) : ResumeDocsState {
        val displayMessage: String
            get() = error.toDisplayMessage()
    }
}

sealed interface ResumeDocsIntent : MVIIntent {
    data object Load : ResumeDocsIntent
    data object Refresh : ResumeDocsIntent
    data class SelectCategory(val category: DocumentCategory?) : ResumeDocsIntent
    data class RequestDownload(val document: DocumentDto) : ResumeDocsIntent
}

sealed interface ResumeDocsAction : MVIAction {
    data class TriggerDownload(val url: String, val filename: String) : ResumeDocsAction
    data class ShowError(val message: String) : ResumeDocsAction
}
