package codes.cy.portfolio.admin.features.documents

import codes.cy.portfolio.admin.crypto.sha256Hex
import codes.cy.portfolio.admin.data.AdminCommandApi
import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.fold
import codes.cy.portfolio.admin.data.toAdminDisplayMessage
import codes.cy.portfolio.admin.platform.AllowedUploadMimes
import codes.cy.portfolio.admin.platform.SelectedUploadFile
import codes.cy.portfolio.admin.platform.pickUploadFile
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.PresignUploadRequest
import codes.cy.portfolio.core.pagination.QueryOptions
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.recover
import pro.respawn.flowmvi.plugins.reduce

object AdminDocumentsStoreFactory {
    private const val PAGE_SIZE: Int = 20
    private const val MAX_UPLOAD_BYTES: Long = 25L * 1024L * 1024L

    fun create(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
        filePicker: suspend () -> SelectedUploadFile? = ::pickUploadFile,
        hashContext: CoroutineContext = Dispatchers.Default,
    ): Store<AdminDocumentsState, AdminDocumentsIntent, AdminDocumentsAction> =
        store(initial = AdminDocumentsState()) {
            recover { cause ->
                if (cause is CancellationException) throw cause
                updateState {
                    copy(
                        isLoading = false,
                        uploadStage = UploadStage.IDLE,
                        errorMessage = cause.message ?: "Document operation failed.",
                    )
                }
                null
            }

            init {
                loadDocumentsPage(queryApi, QueryOptions.DEFAULT_PAGE)
            }

            reduce { intent ->
                when (intent) {
                    is AdminDocumentsIntent.Load -> {
                        var currentPage = QueryOptions.DEFAULT_PAGE
                        updateState {
                            currentPage = page
                            copy(isLoading = true, errorMessage = null, statusMessage = null)
                        }
                        loadDocumentsPage(queryApi, currentPage)
                    }

                    is AdminDocumentsIntent.GoToPage -> {
                        val safePage = intent.page.coerceAtLeast(QueryOptions.DEFAULT_PAGE)
                        updateState { copy(isLoading = true, errorMessage = null, statusMessage = null) }
                        loadDocumentsPage(queryApi, safePage)
                    }

                    is AdminDocumentsIntent.PickFile -> {
                        val picked = filePicker() ?: return@reduce
                        stageSelectedFile(picked, hashContext)
                    }

                    is AdminDocumentsIntent.SelectFile -> {
                        stageSelectedFile(intent.file, hashContext)
                    }

                    is AdminDocumentsIntent.TitleChanged -> updateState {
                        copy(titleInput = intent.title, errorMessage = null)
                    }

                    is AdminDocumentsIntent.CategoryChanged -> updateState {
                        copy(category = intent.category)
                    }

                    is AdminDocumentsIntent.IsPublicChanged -> updateState {
                        copy(isPublic = intent.isPublic)
                    }

                    is AdminDocumentsIntent.DismissFeedback -> updateState {
                        copy(errorMessage = null, statusMessage = null)
                    }

                    is AdminDocumentsIntent.UploadSelectedFile ->
                        executePresignedUpload(queryApi, commandApi)

                    is AdminDocumentsIntent.DeleteDocument -> {
                        var currentPage = QueryOptions.DEFAULT_PAGE
                        updateState {
                            currentPage = page
                            copy(isLoading = true, errorMessage = null, statusMessage = null)
                        }
                        commandApi.deleteDocument(intent.id).fold(
                            onSuccess = {
                                loadDocumentsPage(queryApi, currentPage)
                                updateState { copy(statusMessage = "Document removed from storage and DB.") }
                            },
                            onFailure = { err ->
                                updateState { copy(isLoading = false, errorMessage = err.toAdminDisplayMessage()) }
                            },
                        )
                    }
                }
            }
        }

    private suspend fun PipelineContext<AdminDocumentsState, AdminDocumentsIntent, AdminDocumentsAction>.stageSelectedFile(
        file: SelectedUploadFile,
        hashContext: CoroutineContext,
    ) {
        val byteSize = file.bytes.size.toLong()
        if (byteSize !in 1L..MAX_UPLOAD_BYTES) {
            updateState {
                copy(
                    selectedFile = null,
                    computedSha256 = null,
                    uploadStage = UploadStage.IDLE,
                    errorMessage = "File '${file.filename}' must be between 1 byte and 25 MB.",
                )
            }
            return
        }
        if (file.mimeType !in AllowedUploadMimes.ALL) {
            updateState {
                copy(
                    selectedFile = null,
                    computedSha256 = null,
                    uploadStage = UploadStage.IDLE,
                    errorMessage = "Unsupported file type '${file.mimeType}'. Allowed: PDF, PNG, JPEG, WebP, Markdown, Text.",
                )
            }
            return
        }

        updateState {
            copy(
                uploadStage = UploadStage.HASHING,
                errorMessage = null,
                statusMessage = null,
            )
        }
        val sha256 = withContext(hashContext) { file.bytes.sha256Hex() }
        val suggestedTitle = file.filename.substringBeforeLast('.').ifEmpty { file.filename }
        updateState {
            copy(
                uploadStage = UploadStage.IDLE,
                selectedFile = file,
                computedSha256 = sha256,
                titleInput = titleInput.ifBlank { suggestedTitle },
            )
        }
    }

    private suspend fun PipelineContext<AdminDocumentsState, AdminDocumentsIntent, AdminDocumentsAction>.executePresignedUpload(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ) {
        var snapshot = AdminDocumentsState()
        updateState {
            snapshot = this
            copy(errorMessage = null, statusMessage = null)
        }
        val file = snapshot.selectedFile
        val sha256 = snapshot.computedSha256
        val trimmedTitle = snapshot.titleInput.trim()

        when {
            file == null || sha256 == null -> {
                updateState { copy(errorMessage = "Choose a file before starting upload.") }
                return
            }
            trimmedTitle.isEmpty() -> {
                updateState { copy(errorMessage = "Document title must not be blank.") }
                return
            }
        }

        updateState { copy(uploadStage = UploadStage.PRESIGNING) }
        val presignResult = commandApi.presignDocumentUpload(
            PresignUploadRequest(
                originalFilename = file.filename,
                mimeType = file.mimeType,
                sizeBytes = file.bytes.size.toLong(),
                sha256 = sha256,
            ),
        )
        val presign = when (presignResult) {
            is codes.cy.portfolio.admin.data.AdminApiResult.Success -> presignResult.value
            is codes.cy.portfolio.admin.data.AdminApiResult.Failure -> {
                updateState {
                    copy(uploadStage = UploadStage.IDLE, errorMessage = presignResult.error.toAdminDisplayMessage())
                }
                return
            }
        }

        updateState { copy(uploadStage = UploadStage.UPLOADING_BINARY) }
        val putResult = commandApi.uploadBinaryToPresignedUrl(
            presignedPutUrl = presign.presignedPutUrl,
            requiredHeaders = presign.requiredHeaders,
            mimeType = file.mimeType,
            bytes = file.bytes,
        )
        if (putResult is codes.cy.portfolio.admin.data.AdminApiResult.Failure) {
            updateState {
                copy(uploadStage = UploadStage.IDLE, errorMessage = putResult.error.toAdminDisplayMessage())
            }
            return
        }

        updateState { copy(uploadStage = UploadStage.CONFIRMING) }
        commandApi.confirmDocumentUpload(
            ConfirmUploadRequest(
                documentId = presign.documentId,
                storageKey = presign.storageKey,
                title = trimmedTitle,
                category = snapshot.category,
                originalFilename = file.filename,
                declaredMimeType = file.mimeType,
                sha256 = sha256,
                isPublic = snapshot.isPublic,
            ),
        ).fold(
            onSuccess = { confirmed ->
                loadDocumentsPage(queryApi, snapshot.page)
                updateState {
                    copy(
                        uploadStage = UploadStage.IDLE,
                        selectedFile = null,
                        computedSha256 = null,
                        titleInput = "",
                        statusMessage = "Uploaded & verified '${confirmed.title}' (${confirmed.mimeType}).",
                    )
                }
            },
            onFailure = { err ->
                updateState {
                    copy(uploadStage = UploadStage.IDLE, errorMessage = err.toAdminDisplayMessage())
                }
            },
        )
    }

    private suspend fun PipelineContext<AdminDocumentsState, AdminDocumentsIntent, AdminDocumentsAction>.loadDocumentsPage(
        queryApi: AdminQueryApi,
        targetPage: Int,
    ) {
        queryApi.getDocuments(QueryOptions(page = targetPage, pageSize = PAGE_SIZE)).fold(
            onSuccess = { paged ->
                updateState {
                    copy(
                        isLoading = false,
                        items = paged.items,
                        page = paged.page,
                        totalPages = paged.totalPages,
                        totalCount = paged.totalCount,
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isLoading = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }
}
