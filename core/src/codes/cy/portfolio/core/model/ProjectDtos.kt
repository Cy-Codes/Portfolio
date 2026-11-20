package codes.cy.portfolio.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectSummaryDto(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String,
    val repoUrl: String? = null,
    val liveUrl: String? = null,
    val coverDocumentId: String? = null,
    val status: ProjectStatus,
    val isFeatured: Boolean,
    val skills: List<SkillDto> = emptyList(),
    val sortOrder: Int,
    val updatedAt: Instant,
)

@Serializable
data class ProjectDetailDto(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String,
    val caseStudyMarkdown: String,
    val repoUrl: String? = null,
    val liveUrl: String? = null,
    val coverDocumentId: String? = null,
    val status: ProjectStatus,
    val isFeatured: Boolean,
    val skills: List<SkillDto> = emptyList(),
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class UpsertProjectRequest(
    val slug: String,
    val title: String,
    val summary: String,
    val caseStudyMarkdown: String,
    val repoUrl: String? = null,
    val liveUrl: String? = null,
    val coverDocumentId: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val isFeatured: Boolean = false,
    val skillIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
)
