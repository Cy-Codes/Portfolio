package codes.cy.portfolio.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class SocialLinkDto(
    val label: String,
    val url: String,
    val iconKey: String? = null,
)

@Serializable
data class ProfileDto(
    val fullName: String,
    val headline: String,
    val bioMarkdown: String,
    val location: String? = null,
    val availability: String? = null,
    val socialLinks: List<SocialLinkDto> = emptyList(),
    val resumeDocumentId: String? = null,
    val updatedAt: Instant,
)

@Serializable
data class UpdateProfileRequest(
    val fullName: String,
    val headline: String,
    val bioMarkdown: String,
    val location: String? = null,
    val availability: String? = null,
    val socialLinks: List<SocialLinkDto> = emptyList(),
    val resumeDocumentId: String? = null,
)

@Serializable
data class SkillDto(
    val id: String,
    val name: String,
    val category: String,
    val isFeatured: Boolean,
    val sortOrder: Int,
)

@Serializable
data class UpsertSkillRequest(
    val name: String,
    val category: String,
    val isFeatured: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class ExperienceDto(
    val id: String,
    val company: String,
    val title: String,
    val location: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isCurrent: Boolean,
    val highlights: List<String> = emptyList(),
    val skills: List<SkillDto> = emptyList(),
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class UpsertExperienceRequest(
    val company: String,
    val title: String,
    val location: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isCurrent: Boolean = false,
    val highlights: List<String> = emptyList(),
    val skillIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
)

@Serializable
data class EducationDto(
    val id: String,
    val institution: String,
    val degree: String,
    val fieldOfStudy: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val sortOrder: Int,
)

@Serializable
data class UpsertEducationRequest(
    val institution: String,
    val degree: String,
    val fieldOfStudy: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class CertificationDto(
    val id: String,
    val name: String,
    val issuer: String,
    val issuedDate: LocalDate,
    val expiresDate: LocalDate? = null,
    val credentialUrl: String? = null,
    val sortOrder: Int,
)

@Serializable
data class UpsertCertificationRequest(
    val name: String,
    val issuer: String,
    val issuedDate: LocalDate,
    val expiresDate: LocalDate? = null,
    val credentialUrl: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class PortfolioOverviewDto(
    val profile: ProfileDto?,
    val experiences: List<ExperienceDto> = emptyList(),
    val education: List<EducationDto> = emptyList(),
    val certifications: List<CertificationDto> = emptyList(),
    val featuredSkills: List<SkillDto> = emptyList(),
)
