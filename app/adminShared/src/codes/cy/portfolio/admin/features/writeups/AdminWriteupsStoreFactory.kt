package codes.cy.portfolio.admin.features.writeups

import codes.cy.portfolio.admin.data.AdminApiResult
import codes.cy.portfolio.admin.data.AdminCommandApi
import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.fold
import codes.cy.portfolio.admin.data.toAdminDisplayMessage
import codes.cy.portfolio.admin.features.buildDemoFenceSnippet
import codes.cy.portfolio.admin.features.estimateReadingTimeMinutes
import codes.cy.portfolio.admin.features.slugifyTitle
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.pagination.QueryOptions
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.recover
import pro.respawn.flowmvi.plugins.reduce

object AdminWriteupsStoreFactory {
    private const val PAGE_SIZE: Int = 20
    private const val DOC_PICKER_LIMIT: Int = 50
    private const val MIN_READING_MINUTES: Int = 1

    fun create(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ): Store<AdminWriteupsState, AdminWriteupsIntent, AdminWriteupsAction> =
        store(initial = AdminWriteupsState()) {
            recover { cause ->
                if (cause is CancellationException) throw cause
                updateState {
                    copy(
                        isLoadingList = false,
                        isLoadingDetail = false,
                        isSaving = false,
                        errorMessage = cause.message ?: "Writeup CMS operation failed.",
                    )
                }
                null
            }

            init {
                loadWriteupsAndDocs(queryApi, QueryOptions.DEFAULT_PAGE)
            }

            reduce { intent ->
                when (intent) {
                    is AdminWriteupsIntent.Load -> {
                        var currentPage = QueryOptions.DEFAULT_PAGE
                        updateState {
                            currentPage = page
                            copy(isLoadingList = true, errorMessage = null, statusMessage = null)
                        }
                        loadWriteupsAndDocs(queryApi, currentPage)
                    }

                    is AdminWriteupsIntent.GoToPage -> {
                        val safePage = intent.page.coerceAtLeast(QueryOptions.DEFAULT_PAGE)
                        updateState { copy(isLoadingList = true, errorMessage = null, statusMessage = null) }
                        loadWriteupsOnly(queryApi, safePage)
                    }

                    is AdminWriteupsIntent.FilterByStatus -> updateState {
                        copy(statusFilter = intent.status)
                    }

                    is AdminWriteupsIntent.SelectWriteup -> {
                        updateState { copy(isLoadingDetail = true, errorMessage = null, statusMessage = null) }
                        queryApi.getWriteupById(intent.id).fold(
                            onSuccess = { detail ->
                                updateState {
                                    copy(
                                        isLoadingDetail = false,
                                        draft = detail.toFormDraft(),
                                    )
                                }
                            },
                            onFailure = { err ->
                                updateState {
                                    copy(isLoadingDetail = false, errorMessage = err.toAdminDisplayMessage())
                                }
                            },
                        )
                    }

                    is AdminWriteupsIntent.NewWriteup -> updateState {
                        copy(draft = WriteupFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminWriteupsIntent.UpdateDraft -> updateState {
                        copy(draft = intent.draft)
                    }

                    is AdminWriteupsIntent.AutoSlugFromTitle -> updateState {
                        copy(draft = draft.copy(slug = slugifyTitle(draft.title)))
                    }

                    is AdminWriteupsIntent.AutoEstimateReadingTime -> updateState {
                        val minutes = estimateReadingTimeMinutes(draft.bodyMarkdown)
                        copy(draft = draft.copy(readingTimeMinInput = minutes.toString()))
                    }

                    is AdminWriteupsIntent.InsertDemoSnippet -> updateState {
                        val fence = buildDemoFenceSnippet(intent.demoId)
                        val updatedBody = when {
                            draft.bodyMarkdown.isBlank() -> "$fence\n"
                            draft.bodyMarkdown.endsWith("\n") -> "${draft.bodyMarkdown}\n$fence\n"
                            else -> "${draft.bodyMarkdown}\n\n$fence\n"
                        }
                        copy(draft = draft.copy(bodyMarkdown = updatedBody))
                    }

                    is AdminWriteupsIntent.DismissFeedback -> updateState {
                        copy(errorMessage = null, statusMessage = null)
                    }

                    is AdminWriteupsIntent.SaveWithStatus ->
                        handleSaveWriteup(queryApi, commandApi, intent.targetStatus)

                    is AdminWriteupsIntent.DeleteWriteup ->
                        handleDeleteWriteup(queryApi, commandApi, intent.id)
                }
            }
        }

    private suspend fun PipelineContext<AdminWriteupsState, AdminWriteupsIntent, AdminWriteupsAction>.loadWriteupsAndDocs(
        queryApi: AdminQueryApi,
        targetPage: Int,
    ) = coroutineScope {
        val writeupsDef = async { queryApi.getWriteups(QueryOptions(page = targetPage, pageSize = PAGE_SIZE)) }
        val docsDef = async { queryApi.getDocuments(QueryOptions(page = 1, pageSize = DOC_PICKER_LIMIT)) }

        val writeupsRes = writeupsDef.await()
        val docsRes = docsDef.await()

        updateState {
            val paged = (writeupsRes as? AdminApiResult.Success)?.value
            val failure = (writeupsRes as? AdminApiResult.Failure)?.error
                ?: (docsRes as? AdminApiResult.Failure)?.error
            copy(
                isLoadingList = false,
                items = paged?.items ?: items,
                page = paged?.page ?: page,
                totalPages = paged?.totalPages ?: totalPages,
                totalCount = paged?.totalCount ?: totalCount,
                availableDocuments = (docsRes as? AdminApiResult.Success)?.value?.items ?: availableDocuments,
                errorMessage = failure?.toAdminDisplayMessage(),
            )
        }
    }

    private suspend fun PipelineContext<AdminWriteupsState, AdminWriteupsIntent, AdminWriteupsAction>.loadWriteupsOnly(
        queryApi: AdminQueryApi,
        targetPage: Int,
    ) {
        queryApi.getWriteups(QueryOptions(page = targetPage, pageSize = PAGE_SIZE)).fold(
            onSuccess = { paged ->
                updateState {
                    copy(
                        isLoadingList = false,
                        items = paged.items,
                        page = paged.page,
                        totalPages = paged.totalPages,
                        totalCount = paged.totalCount,
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isLoadingList = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminWriteupsState, AdminWriteupsIntent, AdminWriteupsAction>.handleSaveWriteup(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
        targetStatus: WriteupStatus,
    ) {
        var currentDraft = WriteupFormDraft()
        var currentPage = QueryOptions.DEFAULT_PAGE
        updateState {
            currentDraft = draft
            currentPage = page
            copy(errorMessage = null, statusMessage = null)
        }
        val readingTimeMin = currentDraft.readingTimeMinInput.trim().toIntOrNull()
        if (readingTimeMin == null || readingTimeMin < MIN_READING_MINUTES) {
            updateState { copy(errorMessage = "Reading time must be an integer >= 1.") }
            return
        }

        val request = UpsertWriteupRequest(
            slug = currentDraft.slug.trim(),
            title = currentDraft.title.trim(),
            subtitle = currentDraft.subtitle.trim().takeIf { it.isNotEmpty() },
            summary = currentDraft.summary.trim(),
            bodyMarkdown = currentDraft.bodyMarkdown,
            tags = currentDraft.parsedTags,
            status = targetStatus,
            readingTimeMin = readingTimeMin,
            coverDocumentId = currentDraft.coverDocumentId?.trim()?.takeIf { it.isNotEmpty() },
        )

        updateState { copy(isSaving = true) }
        val result = currentDraft.editingId?.let { id -> commandApi.updateWriteup(id, request) }
            ?: commandApi.createWriteup(request)

        result.fold(
            onSuccess = { saved ->
                loadWriteupsOnly(queryApi, currentPage)
                val verb = when (saved.status) {
                    WriteupStatus.PUBLISHED -> "Published"
                    WriteupStatus.DRAFT -> "Saved draft"
                    WriteupStatus.ARCHIVED -> "Archived"
                }
                updateState {
                    copy(
                        isSaving = false,
                        draft = saved.toFormDraft(),
                        statusMessage = "$verb writeup '${saved.slug}'.",
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminWriteupsState, AdminWriteupsIntent, AdminWriteupsAction>.handleDeleteWriteup(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
        id: String,
    ) {
        var currentPage = QueryOptions.DEFAULT_PAGE
        updateState {
            currentPage = page
            copy(isSaving = true, errorMessage = null, statusMessage = null)
        }
        commandApi.deleteWriteup(id).fold(
            onSuccess = {
                loadWriteupsOnly(queryApi, currentPage)
                updateState {
                    copy(
                        isSaving = false,
                        draft = if (draft.editingId == id) WriteupFormDraft() else draft,
                        statusMessage = "Writeup deleted.",
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }
}
