package codes.cy.portfolio.admin.features.projects

import codes.cy.portfolio.admin.data.AdminApiResult
import codes.cy.portfolio.admin.data.AdminCommandApi
import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.fold
import codes.cy.portfolio.admin.data.toAdminDisplayMessage
import codes.cy.portfolio.admin.features.slugifyTitle
import codes.cy.portfolio.core.model.UpsertProjectRequest
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

object AdminProjectsStoreFactory {
    private const val PAGE_SIZE: Int = 20
    private const val DOC_PICKER_LIMIT: Int = 50

    fun create(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ): Store<AdminProjectsState, AdminProjectsIntent, AdminProjectsAction> =
        store(initial = AdminProjectsState()) {
            recover { cause ->
                if (cause is CancellationException) throw cause
                updateState {
                    copy(
                        isLoadingList = false,
                        isLoadingDetail = false,
                        isSaving = false,
                        errorMessage = cause.message ?: "Project CMS operation failed.",
                    )
                }
                null
            }

            init {
                loadPageAndLookups(queryApi, QueryOptions.DEFAULT_PAGE)
            }

            reduce { intent ->
                when (intent) {
                    is AdminProjectsIntent.Load -> {
                        var currentPage = QueryOptions.DEFAULT_PAGE
                        updateState {
                            currentPage = page
                            copy(isLoadingList = true, errorMessage = null, statusMessage = null)
                        }
                        loadPageAndLookups(queryApi, currentPage)
                    }

                    is AdminProjectsIntent.GoToPage -> {
                        val safePage = intent.page.coerceAtLeast(QueryOptions.DEFAULT_PAGE)
                        updateState { copy(isLoadingList = true, errorMessage = null, statusMessage = null) }
                        loadProjectsOnly(queryApi, safePage)
                    }

                    is AdminProjectsIntent.SelectProject -> {
                        updateState { copy(isLoadingDetail = true, errorMessage = null, statusMessage = null) }
                        queryApi.getProjectById(intent.id).fold(
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

                    is AdminProjectsIntent.NewProject -> updateState {
                        copy(draft = ProjectFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminProjectsIntent.UpdateDraft -> updateState {
                        copy(draft = intent.draft)
                    }

                    is AdminProjectsIntent.GenerateSlugFromTitle -> updateState {
                        copy(draft = draft.copy(slug = slugifyTitle(draft.title)))
                    }

                    is AdminProjectsIntent.ToggleSkill -> updateState {
                        val nextSkills = if (intent.skillId in draft.selectedSkillIds) {
                            draft.selectedSkillIds - intent.skillId
                        } else {
                            draft.selectedSkillIds + intent.skillId
                        }
                        copy(draft = draft.copy(selectedSkillIds = nextSkills))
                    }

                    is AdminProjectsIntent.DismissFeedback -> updateState {
                        copy(errorMessage = null, statusMessage = null)
                    }

                    is AdminProjectsIntent.SaveProject -> handleSaveProject(queryApi, commandApi)

                    is AdminProjectsIntent.DeleteProject -> handleDeleteProject(queryApi, commandApi, intent.id)
                }
            }
        }

    private suspend fun PipelineContext<AdminProjectsState, AdminProjectsIntent, AdminProjectsAction>.loadPageAndLookups(
        queryApi: AdminQueryApi,
        targetPage: Int,
    ) = coroutineScope {
        val projectsDef = async { queryApi.getProjects(QueryOptions(page = targetPage, pageSize = PAGE_SIZE)) }
        val skillsDef = async { queryApi.getSkills() }
        val docsDef = async { queryApi.getDocuments(QueryOptions(page = 1, pageSize = DOC_PICKER_LIMIT)) }

        val projectsRes = projectsDef.await()
        val skillsRes = skillsDef.await()
        val docsRes = docsDef.await()

        updateState {
            val paged = (projectsRes as? AdminApiResult.Success)?.value
            val failure = (projectsRes as? AdminApiResult.Failure)?.error
                ?: (skillsRes as? AdminApiResult.Failure)?.error
                ?: (docsRes as? AdminApiResult.Failure)?.error

            copy(
                isLoadingList = false,
                items = paged?.items ?: items,
                page = paged?.page ?: page,
                totalPages = paged?.totalPages ?: totalPages,
                totalCount = paged?.totalCount ?: totalCount,
                availableSkills = (skillsRes as? AdminApiResult.Success)?.value ?: availableSkills,
                availableDocuments = (docsRes as? AdminApiResult.Success)?.value?.items ?: availableDocuments,
                errorMessage = failure?.toAdminDisplayMessage(),
            )
        }
    }

    private suspend fun PipelineContext<AdminProjectsState, AdminProjectsIntent, AdminProjectsAction>.loadProjectsOnly(
        queryApi: AdminQueryApi,
        targetPage: Int,
    ) {
        queryApi.getProjects(QueryOptions(page = targetPage, pageSize = PAGE_SIZE)).fold(
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

    private suspend fun PipelineContext<AdminProjectsState, AdminProjectsIntent, AdminProjectsAction>.handleSaveProject(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ) {
        var currentDraft = ProjectFormDraft()
        var currentPage = QueryOptions.DEFAULT_PAGE
        updateState {
            currentDraft = draft
            currentPage = page
            copy(errorMessage = null, statusMessage = null)
        }
        val sortOrder = currentDraft.sortOrderInput.trim().toIntOrNull()
        if (sortOrder == null) {
            updateState { copy(errorMessage = "Sort order must be an integer.") }
            return
        }

        val request = UpsertProjectRequest(
            slug = currentDraft.slug.trim(),
            title = currentDraft.title.trim(),
            summary = currentDraft.summary.trim(),
            caseStudyMarkdown = currentDraft.caseStudyMarkdown,
            repoUrl = currentDraft.repoUrl.trim().takeIf { it.isNotEmpty() },
            liveUrl = currentDraft.liveUrl.trim().takeIf { it.isNotEmpty() },
            coverDocumentId = currentDraft.coverDocumentId?.trim()?.takeIf { it.isNotEmpty() },
            status = currentDraft.status,
            isFeatured = currentDraft.isFeatured,
            skillIds = currentDraft.selectedSkillIds.toList(),
            sortOrder = sortOrder,
        )

        updateState { copy(isSaving = true) }
        val result = currentDraft.editingId?.let { id -> commandApi.updateProject(id, request) }
            ?: commandApi.createProject(request)

        result.fold(
            onSuccess = { saved ->
                loadProjectsOnly(queryApi, currentPage)
                updateState {
                    copy(
                        isSaving = false,
                        draft = saved.toFormDraft(),
                        statusMessage = if (currentDraft.editingId == null) {
                            "Project '${saved.slug}' created."
                        } else {
                            "Project '${saved.slug}' updated."
                        },
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminProjectsState, AdminProjectsIntent, AdminProjectsAction>.handleDeleteProject(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
        id: String,
    ) {
        var currentPage = QueryOptions.DEFAULT_PAGE
        updateState {
            currentPage = page
            copy(isSaving = true, errorMessage = null, statusMessage = null)
        }
        commandApi.deleteProject(id).fold(
            onSuccess = {
                loadProjectsOnly(queryApi, currentPage)
                updateState {
                    copy(
                        isSaving = false,
                        draft = if (draft.editingId == id) ProjectFormDraft() else draft,
                        statusMessage = "Project deleted.",
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }
}
