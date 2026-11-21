package codes.cy.portfolio.admin.features.career

import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.SocialLinkDto
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

enum class CareerSectionTab(val label: String) {
    PROFILE("Profile"),
    EXPERIENCES("Experiences"),
    EDUCATION("Education"),
    CERTIFICATIONS("Certifications"),
    SKILLS("Skills"),
}

data class ProfileFormDraft(
    val fullName: String = "",
    val headline: String = "",
    val bioMarkdown: String = "",
    val location: String = "",
    val availability: String = "",
    val resumeDocumentId: String? = null,
    val socialLinks: List<SocialLinkDto> = emptyList(),
)

data class ExperienceFormDraft(
    val editingId: String? = null,
    val company: String = "",
    val title: String = "",
    val location: String = "",
    val startDateInput: String = "",
    val endDateInput: String = "",
    val isCurrent: Boolean = false,
    val highlightsInput: String = "",
    val selectedSkillIds: Set<String> = emptySet(),
    val sortOrderInput: String = "0",
)

data class EducationFormDraft(
    val editingId: String? = null,
    val institution: String = "",
    val degree: String = "",
    val fieldOfStudy: String = "",
    val startDateInput: String = "",
    val endDateInput: String = "",
    val sortOrderInput: String = "0",
)

data class CertificationFormDraft(
    val editingId: String? = null,
    val name: String = "",
    val issuer: String = "",
    val issuedDateInput: String = "",
    val expiresDateInput: String = "",
    val credentialUrl: String = "",
    val sortOrderInput: String = "0",
)

data class SkillFormDraft(
    val editingId: String? = null,
    val name: String = "",
    val category: String = "",
    val isFeatured: Boolean = false,
    val sortOrderInput: String = "0",
)

data class AdminCareerState(
    val activeTab: CareerSectionTab = CareerSectionTab.PROFILE,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val profileDraft: ProfileFormDraft = ProfileFormDraft(),
    val experiences: List<ExperienceDto> = emptyList(),
    val experienceDraft: ExperienceFormDraft = ExperienceFormDraft(),
    val educationList: List<EducationDto> = emptyList(),
    val educationDraft: EducationFormDraft = EducationFormDraft(),
    val certifications: List<CertificationDto> = emptyList(),
    val certificationDraft: CertificationFormDraft = CertificationFormDraft(),
    val skills: List<SkillDto> = emptyList(),
    val skillDraft: SkillFormDraft = SkillFormDraft(),
    val documents: List<DocumentDto> = emptyList(),
    val errorMessage: String? = null,
    val statusMessage: String? = null,
) : MVIState

sealed interface AdminCareerIntent : MVIIntent {
    data object Load : AdminCareerIntent
    data class SelectTab(val tab: CareerSectionTab) : AdminCareerIntent
    data class UpdateProfileDraft(val draft: ProfileFormDraft) : AdminCareerIntent
    data class AddSocialLink(val label: String, val url: String) : AdminCareerIntent
    data class RemoveSocialLink(val index: Int) : AdminCareerIntent
    data object SaveProfile : AdminCareerIntent

    data class UpdateExperienceDraft(val draft: ExperienceFormDraft) : AdminCareerIntent
    data class EditExperience(val item: ExperienceDto) : AdminCareerIntent
    data object NewExperience : AdminCareerIntent
    data object SaveExperience : AdminCareerIntent
    data class DeleteExperience(val id: String) : AdminCareerIntent

    data class UpdateEducationDraft(val draft: EducationFormDraft) : AdminCareerIntent
    data class EditEducation(val item: EducationDto) : AdminCareerIntent
    data object NewEducation : AdminCareerIntent
    data object SaveEducation : AdminCareerIntent
    data class DeleteEducation(val id: String) : AdminCareerIntent

    data class UpdateCertificationDraft(val draft: CertificationFormDraft) : AdminCareerIntent
    data class EditCertification(val item: CertificationDto) : AdminCareerIntent
    data object NewCertification : AdminCareerIntent
    data object SaveCertification : AdminCareerIntent
    data class DeleteCertification(val id: String) : AdminCareerIntent

    data class UpdateSkillDraft(val draft: SkillFormDraft) : AdminCareerIntent
    data class EditSkill(val item: SkillDto) : AdminCareerIntent
    data object NewSkill : AdminCareerIntent
    data object SaveSkill : AdminCareerIntent
    data class DeleteSkill(val id: String) : AdminCareerIntent

    data object DismissFeedback : AdminCareerIntent
}

sealed interface AdminCareerAction : MVIAction

fun ProfileDto.toFormDraft(): ProfileFormDraft =
    ProfileFormDraft(
        fullName = fullName,
        headline = headline,
        bioMarkdown = bioMarkdown,
        location = location.orEmpty(),
        availability = availability.orEmpty(),
        resumeDocumentId = resumeDocumentId,
        socialLinks = socialLinks,
    )

fun ExperienceDto.toFormDraft(): ExperienceFormDraft =
    ExperienceFormDraft(
        editingId = id,
        company = company,
        title = title,
        location = location.orEmpty(),
        startDateInput = startDate.toString(),
        endDateInput = endDate?.toString().orEmpty(),
        isCurrent = isCurrent,
        highlightsInput = highlights.joinToString("\n"),
        selectedSkillIds = skills.map { it.id }.toSet(),
        sortOrderInput = sortOrder.toString(),
    )

fun EducationDto.toFormDraft(): EducationFormDraft =
    EducationFormDraft(
        editingId = id,
        institution = institution,
        degree = degree,
        fieldOfStudy = fieldOfStudy.orEmpty(),
        startDateInput = startDate.toString(),
        endDateInput = endDate?.toString().orEmpty(),
        sortOrderInput = sortOrder.toString(),
    )

fun CertificationDto.toFormDraft(): CertificationFormDraft =
    CertificationFormDraft(
        editingId = id,
        name = name,
        issuer = issuer,
        issuedDateInput = issuedDate.toString(),
        expiresDateInput = expiresDate?.toString().orEmpty(),
        credentialUrl = credentialUrl.orEmpty(),
        sortOrderInput = sortOrder.toString(),
    )

fun SkillDto.toFormDraft(): SkillFormDraft =
    SkillFormDraft(
        editingId = id,
        name = name,
        category = category,
        isFeatured = isFeatured,
        sortOrderInput = sortOrder.toString(),
    )
