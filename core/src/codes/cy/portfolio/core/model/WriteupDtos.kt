package codes.cy.portfolio.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class WriteupSummaryDto(
    val id: String,
    val slug: String,
    val title: String,
    val subtitle: String? = null,
    val summary: String,
    val tags: List<String> = emptyList(),
    val status: WriteupStatus,
    val readingTimeMin: Int,
    val coverDocumentId: String? = null,
    val publishedAt: Instant? = null,
    val updatedAt: Instant,
)

@Serializable
data class WriteupDetailDto(
    val id: String,
    val slug: String,
    val title: String,
    val subtitle: String? = null,
    val summary: String,
    val bodyMarkdown: String,
    val tags: List<String> = emptyList(),
    val status: WriteupStatus,
    val readingTimeMin: Int,
    val coverDocumentId: String? = null,
    val publishedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class UpsertWriteupRequest(
    val slug: String,
    val title: String,
    val subtitle: String? = null,
    val summary: String,
    val bodyMarkdown: String,
    val tags: List<String> = emptyList(),
    val status: WriteupStatus = WriteupStatus.DRAFT,
    val readingTimeMin: Int = DEFAULT_READING_TIME_MIN,
    val coverDocumentId: String? = null,
) {
    companion object {
        const val DEFAULT_READING_TIME_MIN: Int = 1
    }
}
