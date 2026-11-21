package codes.cy.portfolio.admin.features.career

import codes.cy.portfolio.admin.data.AdminApiResult
import codes.cy.portfolio.admin.data.AdminCommandApi
import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.fold
import codes.cy.portfolio.admin.data.onSuccess
import codes.cy.portfolio.admin.data.toAdminDisplayMessage
import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.SocialLinkDto
import codes.cy.portfolio.core.model.UpdateProfileRequest
import codes.cy.portfolio.core.model.UpsertCertificationRequest
import codes.cy.portfolio.core.model.UpsertEducationRequest
import codes.cy.portfolio.core.model.UpsertExperienceRequest
import codes.cy.portfolio.core.model.UpsertSkillRequest
import codes.cy.portfolio.core.pagination.QueryOptions
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.recover
import pro.respawn.flowmvi.plugins.reduce

object AdminCareerStoreFactory {
    private const val DOC_PICKER_PAGE_SIZE: Int = 50

    fun create(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ): Store<AdminCareerState, AdminCareerIntent, AdminCareerAction> =
        store(initial = AdminCareerState()) {
            recover { cause ->
                if (cause is CancellationException) throw cause
                updateState {
                    copy(
                        isLoading = false,
                        isSaving = false,
                        errorMessage = cause.message ?: "Unexpected career editor error.",
                    )
                }
                null
            }

            init {
                loadAllCareerData(queryApi)
            }

            reduce { intent ->
                when (intent) {
                    is AdminCareerIntent.Load -> {
                        updateState { copy(isLoading = true, errorMessage = null, statusMessage = null) }
                        loadAllCareerData(queryApi)
                    }

                    is AdminCareerIntent.SelectTab -> updateState {
                        copy(activeTab = intent.tab, errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.DismissFeedback -> updateState {
                        copy(errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.UpdateProfileDraft -> updateState {
                        copy(profileDraft = intent.draft)
                    }

                    is AdminCareerIntent.AddSocialLink -> updateState {
                        val label = intent.label.trim()
                        val url = intent.url.trim()
                        if (label.isEmpty() || url.isEmpty()) {
                            copy(errorMessage = "Social link requires both label and URL.")
                        } else {
                            copy(
                                profileDraft = profileDraft.copy(
                                    socialLinks = profileDraft.socialLinks + SocialLinkDto(label = label, url = url),
                                ),
                                errorMessage = null,
                            )
                        }
                    }

                    is AdminCareerIntent.RemoveSocialLink -> updateState {
                        copy(
                            profileDraft = profileDraft.copy(
                                socialLinks = profileDraft.socialLinks.filterIndexed { index, _ -> index != intent.index },
                            ),
                        )
                    }

                    is AdminCareerIntent.SaveProfile -> handleSaveProfile(commandApi)

                    is AdminCareerIntent.UpdateExperienceDraft -> updateState {
                        copy(experienceDraft = intent.draft)
                    }

                    is AdminCareerIntent.EditExperience -> updateState {
                        copy(experienceDraft = intent.item.toFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.NewExperience -> updateState {
                        copy(experienceDraft = ExperienceFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.SaveExperience -> handleSaveExperience(queryApi, commandApi)

                    is AdminCareerIntent.DeleteExperience -> handleDelete(
                        performDelete = { commandApi.deleteExperience(intent.id) },
                        reload = { refreshExperiences(queryApi) },
                        successMessage = "Experience deleted.",
                    )

                    is AdminCareerIntent.UpdateEducationDraft -> updateState {
                        copy(educationDraft = intent.draft)
                    }

                    is AdminCareerIntent.EditEducation -> updateState {
                        copy(educationDraft = intent.item.toFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.NewEducation -> updateState {
                        copy(educationDraft = EducationFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.SaveEducation -> handleSaveEducation(queryApi, commandApi)

                    is AdminCareerIntent.DeleteEducation -> handleDelete(
                        performDelete = { commandApi.deleteEducation(intent.id) },
                        reload = { refreshEducation(queryApi) },
                        successMessage = "Education record deleted.",
                    )

                    is AdminCareerIntent.UpdateCertificationDraft -> updateState {
                        copy(certificationDraft = intent.draft)
                    }

                    is AdminCareerIntent.EditCertification -> updateState {
                        copy(certificationDraft = intent.item.toFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.NewCertification -> updateState {
                        copy(certificationDraft = CertificationFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.SaveCertification -> handleSaveCertification(queryApi, commandApi)

                    is AdminCareerIntent.DeleteCertification -> handleDelete(
                        performDelete = { commandApi.deleteCertification(intent.id) },
                        reload = { refreshCertifications(queryApi) },
                        successMessage = "Certification deleted.",
                    )

                    is AdminCareerIntent.UpdateSkillDraft -> updateState {
                        copy(skillDraft = intent.draft)
                    }

                    is AdminCareerIntent.EditSkill -> updateState {
                        copy(skillDraft = intent.item.toFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.NewSkill -> updateState {
                        copy(skillDraft = SkillFormDraft(), errorMessage = null, statusMessage = null)
                    }

                    is AdminCareerIntent.SaveSkill -> handleSaveSkill(queryApi, commandApi)

                    is AdminCareerIntent.DeleteSkill -> handleDelete(
                        performDelete = { commandApi.deleteSkill(intent.id) },
                        reload = { refreshSkills(queryApi) },
                        successMessage = "Skill deleted.",
                    )
                }
            }
        }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.loadAllCareerData(
        queryApi: AdminQueryApi,
    ) = coroutineScope {
        val profileDef = async { queryApi.getProfile() }
        val expDef = async { queryApi.getExperiences() }
        val eduDef = async { queryApi.getEducation() }
        val certDef = async { queryApi.getCertifications() }
        val skillsDef = async { queryApi.getSkills() }
        val docsDef = async { queryApi.getDocuments(QueryOptions(page = 1, pageSize = DOC_PICKER_PAGE_SIZE)) }

        val profileRes = profileDef.await()
        val expRes = expDef.await()
        val eduRes = eduDef.await()
        val certRes = certDef.await()
        val skillsRes = skillsDef.await()
        val docsRes = docsDef.await()

        val firstHardError = listOfNotNull(
            (profileRes as? AdminApiResult.Failure)?.error?.takeIf { it !is ApiError.NotFound },
            (expRes as? AdminApiResult.Failure)?.error,
            (eduRes as? AdminApiResult.Failure)?.error,
            (certRes as? AdminApiResult.Failure)?.error,
            (skillsRes as? AdminApiResult.Failure)?.error,
            (docsRes as? AdminApiResult.Failure)?.error,
        ).firstOrNull()

        updateState {
            copy(
                isLoading = false,
                profileDraft = (profileRes as? AdminApiResult.Success)?.value?.toFormDraft() ?: profileDraft,
                experiences = (expRes as? AdminApiResult.Success)?.value ?: experiences,
                educationList = (eduRes as? AdminApiResult.Success)?.value ?: educationList,
                certifications = (certRes as? AdminApiResult.Success)?.value ?: certifications,
                skills = (skillsRes as? AdminApiResult.Success)?.value ?: skills,
                documents = (docsRes as? AdminApiResult.Success)?.value?.items ?: documents,
                errorMessage = firstHardError?.toAdminDisplayMessage(),
            )
        }
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.handleSaveProfile(
        commandApi: AdminCommandApi,
    ) {
        var draft = ProfileFormDraft()
        updateState {
            draft = profileDraft
            copy(isSaving = true, errorMessage = null, statusMessage = null)
        }
        val request = UpdateProfileRequest(
            fullName = draft.fullName.trim(),
            headline = draft.headline.trim(),
            bioMarkdown = draft.bioMarkdown,
            location = draft.location.trim().takeIf { it.isNotEmpty() },
            availability = draft.availability.trim().takeIf { it.isNotEmpty() },
            socialLinks = draft.socialLinks,
            resumeDocumentId = draft.resumeDocumentId?.trim()?.takeIf { it.isNotEmpty() },
        )
        commandApi.updateProfile(request).fold(
            onSuccess = { saved ->
                updateState {
                    copy(
                        isSaving = false,
                        profileDraft = saved.toFormDraft(),
                        statusMessage = "Profile saved.",
                    )
                }
            },
            onFailure = { err ->
                updateState {
                    copy(isSaving = false, errorMessage = err.toAdminDisplayMessage())
                }
            },
        )
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.handleSaveExperience(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ) {
        var draft = ExperienceFormDraft()
        updateState {
            draft = experienceDraft
            copy(errorMessage = null, statusMessage = null)
        }
        val startDate = parseRequiredIsoDate(draft.startDateInput, "Start Date")
            ?: return failValidation("Start Date must use ISO format YYYY-MM-DD.")
        val endDate = if (draft.isCurrent || draft.endDateInput.isBlank()) {
            null
        } else {
            parseRequiredIsoDate(draft.endDateInput, "End Date")
                ?: return failValidation("End Date must use ISO format YYYY-MM-DD or remain blank.")
        }
        val sortOrder = draft.sortOrderInput.trim().toIntOrNull()
            ?: return failValidation("Sort order must be an integer.")

        val request = UpsertExperienceRequest(
            company = draft.company.trim(),
            title = draft.title.trim(),
            location = draft.location.trim().takeIf { it.isNotEmpty() },
            startDate = startDate,
            endDate = endDate,
            isCurrent = draft.isCurrent,
            highlights = draft.highlightsInput.lines().map { it.trim() }.filter { it.isNotEmpty() },
            skillIds = draft.selectedSkillIds.toList(),
            sortOrder = sortOrder,
        )

        updateState { copy(isSaving = true) }
        val result = draft.editingId?.let { id -> commandApi.updateExperience(id, request) }
            ?: commandApi.createExperience(request)

        result.fold(
            onSuccess = {
                refreshExperiences(queryApi)
                updateState {
                    copy(
                        isSaving = false,
                        experienceDraft = ExperienceFormDraft(),
                        statusMessage = if (draft.editingId == null) "Experience created." else "Experience updated.",
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.handleSaveEducation(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ) {
        var draft = EducationFormDraft()
        updateState {
            draft = educationDraft
            copy(errorMessage = null, statusMessage = null)
        }
        val startDate = parseRequiredIsoDate(draft.startDateInput, "Start Date")
            ?: return failValidation("Start Date must use ISO format YYYY-MM-DD.")
        val endDate = if (draft.endDateInput.isBlank()) {
            null
        } else {
            parseRequiredIsoDate(draft.endDateInput, "End Date")
                ?: return failValidation("End Date must use ISO format YYYY-MM-DD or remain blank.")
        }
        val sortOrder = draft.sortOrderInput.trim().toIntOrNull()
            ?: return failValidation("Sort order must be an integer.")

        val request = UpsertEducationRequest(
            institution = draft.institution.trim(),
            degree = draft.degree.trim(),
            fieldOfStudy = draft.fieldOfStudy.trim().takeIf { it.isNotEmpty() },
            startDate = startDate,
            endDate = endDate,
            sortOrder = sortOrder,
        )

        updateState { copy(isSaving = true) }
        val result = draft.editingId?.let { id -> commandApi.updateEducation(id, request) }
            ?: commandApi.createEducation(request)

        result.fold(
            onSuccess = {
                refreshEducation(queryApi)
                updateState {
                    copy(
                        isSaving = false,
                        educationDraft = EducationFormDraft(),
                        statusMessage = if (draft.editingId == null) "Education created." else "Education updated.",
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.handleSaveCertification(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ) {
        var draft = CertificationFormDraft()
        updateState {
            draft = certificationDraft
            copy(errorMessage = null, statusMessage = null)
        }
        val issuedDate = parseRequiredIsoDate(draft.issuedDateInput, "Issued Date")
            ?: return failValidation("Issued Date must use ISO format YYYY-MM-DD.")
        val expiresDate = if (draft.expiresDateInput.isBlank()) {
            null
        } else {
            parseRequiredIsoDate(draft.expiresDateInput, "Expires Date")
                ?: return failValidation("Expires Date must use ISO format YYYY-MM-DD or remain blank.")
        }
        val sortOrder = draft.sortOrderInput.trim().toIntOrNull()
            ?: return failValidation("Sort order must be an integer.")

        val request = UpsertCertificationRequest(
            name = draft.name.trim(),
            issuer = draft.issuer.trim(),
            issuedDate = issuedDate,
            expiresDate = expiresDate,
            credentialUrl = draft.credentialUrl.trim().takeIf { it.isNotEmpty() },
            sortOrder = sortOrder,
        )

        updateState { copy(isSaving = true) }
        val result = draft.editingId?.let { id -> commandApi.updateCertification(id, request) }
            ?: commandApi.createCertification(request)

        result.fold(
            onSuccess = {
                refreshCertifications(queryApi)
                updateState {
                    copy(
                        isSaving = false,
                        certificationDraft = CertificationFormDraft(),
                        statusMessage = if (draft.editingId == null) "Certification created." else "Certification updated.",
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.handleSaveSkill(
        queryApi: AdminQueryApi,
        commandApi: AdminCommandApi,
    ) {
        var draft = SkillFormDraft()
        updateState {
            draft = skillDraft
            copy(errorMessage = null, statusMessage = null)
        }
        val sortOrder = draft.sortOrderInput.trim().toIntOrNull()
            ?: return failValidation("Sort order must be an integer.")

        val request = UpsertSkillRequest(
            name = draft.name.trim(),
            category = draft.category.trim(),
            isFeatured = draft.isFeatured,
            sortOrder = sortOrder,
        )

        updateState { copy(isSaving = true) }
        val result = draft.editingId?.let { id -> commandApi.updateSkill(id, request) }
            ?: commandApi.createSkill(request)

        result.fold(
            onSuccess = {
                refreshSkills(queryApi)
                updateState {
                    copy(
                        isSaving = false,
                        skillDraft = SkillFormDraft(),
                        statusMessage = if (draft.editingId == null) "Skill created." else "Skill updated.",
                    )
                }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.handleDelete(
        performDelete: suspend () -> AdminApiResult<Unit>,
        reload: suspend () -> Unit,
        successMessage: String,
    ) {
        updateState { copy(isSaving = true, errorMessage = null, statusMessage = null) }
        performDelete().fold(
            onSuccess = {
                reload()
                updateState { copy(isSaving = false, statusMessage = successMessage) }
            },
            onFailure = { err ->
                updateState { copy(isSaving = false, errorMessage = err.toAdminDisplayMessage()) }
            },
        )
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.refreshExperiences(
        queryApi: AdminQueryApi,
    ) {
        queryApi.getExperiences().onSuccess { items ->
            updateState { copy(experiences = items) }
        }
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.refreshEducation(
        queryApi: AdminQueryApi,
    ) {
        queryApi.getEducation().onSuccess { items ->
            updateState { copy(educationList = items) }
        }
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.refreshCertifications(
        queryApi: AdminQueryApi,
    ) {
        queryApi.getCertifications().onSuccess { items ->
            updateState { copy(certifications = items) }
        }
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.refreshSkills(
        queryApi: AdminQueryApi,
    ) {
        queryApi.getSkills().onSuccess { items ->
            updateState { copy(skills = items) }
        }
    }

    private suspend fun PipelineContext<AdminCareerState, AdminCareerIntent, AdminCareerAction>.failValidation(
        message: String,
    ) {
        updateState { copy(isSaving = false, errorMessage = message) }
    }

    private fun parseRequiredIsoDate(raw: String, ignoredLabel: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
}
