package codes.cy.portfolio.admin.features.documents

import codes.cy.portfolio.admin.platform.SelectedUploadFile
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.pagination.QueryOptions
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

enum class UploadStage(val label: String) {
    IDLE("Ready"),
    HASHING("Computing SHA-256 digest"),
    PRESIGNING("Requesting presigned PUT URL"),
    UPLOADING_BINARY("Uploading raw bytes to R2 / MinIO"),
    CONFIRMING("Verifying magic-byte MIME & persisting metadata"),
    ;

    val isInProgress: Boolean
        get() = this != IDLE
}

data class AdminDocumentsState(
    val items: List<DocumentDto> = emptyList(),
    val page: Int = QueryOptions.DEFAULT_PAGE,
    val totalPages: Int = 0,
    val totalCount: Long = 0L,
    val isLoading: Boolean = true,
    val uploadStage: UploadStage = UploadStage.IDLE,
    val selectedFile: SelectedUploadFile? = null,
    val computedSha256: String? = null,
    val titleInput: String = "",
    val category: DocumentCategory = DocumentCategory.ATTACHMENT,
    val isPublic: Boolean = true,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) : MVIState

sealed interface AdminDocumentsIntent : MVIIntent {
    data object Load : AdminDocumentsIntent
    data class GoToPage(val page: Int) : AdminDocumentsIntent
    data object PickFile : AdminDocumentsIntent
    data class SelectFile(val file: SelectedUploadFile) : AdminDocumentsIntent
    data class TitleChanged(val title: String) : AdminDocumentsIntent
    data class CategoryChanged(val category: DocumentCategory) : AdminDocumentsIntent
    data class IsPublicChanged(val isPublic: Boolean) : AdminDocumentsIntent
    data object UploadSelectedFile : AdminDocumentsIntent
    data class DeleteDocument(val id: String) : AdminDocumentsIntent
    data object DismissFeedback : AdminDocumentsIntent
}

sealed interface AdminDocumentsAction : MVIAction
