package codes.cy.portfolio.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WriteupStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
}

@Serializable
enum class ProjectStatus {
    ACTIVE,
    ARCHIVED,
    PROTOTYPE,
}

@Serializable
enum class DocumentCategory {
    RESUME,
    PAPER,
    SLIDES,
    IMAGE,
    ATTACHMENT,
}

@Serializable
enum class AnalyticsEventType {
    PAGE_VIEW,
    WRITEUP_READ,
    PROJECT_CLICK,
    DOCUMENT_DOWNLOAD,
}

@Serializable
enum class ViewportTier {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE,
    EXTRA_LARGE,
}

@Serializable
enum class ClientPlatform {
    WEB,
    ANDROID,
    IOS,
    DESKTOP,
}

@Serializable
enum class SortDirection(val wireValue: String) {
    @SerialName("asc")
    ASC("asc"),

    @SerialName("desc")
    DESC("desc"),
    ;

    companion object {
        fun parse(raw: String?): SortDirection =
            when (raw?.trim()?.lowercase()) {
                null, "", "asc" -> ASC
                "desc" -> DESC
                else -> throw IllegalArgumentException(
                    "Invalid sortDirection '$raw'. Expected 'asc' or 'desc'.",
                )
            }
    }
}

private const val DAYS_SEVEN: Int = 7
private const val DAYS_THIRTY: Int = 30
private const val DAYS_NINETY: Int = 90

@Serializable
enum class StatsTimeRange(val wireValue: String, val days: Int?) {
    @SerialName("7d")
    SEVEN_DAYS("7d", DAYS_SEVEN),

    @SerialName("30d")
    THIRTY_DAYS("30d", DAYS_THIRTY),

    @SerialName("90d")
    NINETY_DAYS("90d", DAYS_NINETY),

    @SerialName("all")
    ALL("all", null),
    ;

    companion object {
        fun parse(raw: String?): StatsTimeRange =
            when (raw?.trim()?.lowercase()) {
                null, "", "30d" -> THIRTY_DAYS
                "7d" -> SEVEN_DAYS
                "90d" -> NINETY_DAYS
                "all" -> ALL
                else -> throw IllegalArgumentException(
                    "Invalid stats range '$raw'. Expected 7d, 30d, 90d, or all.",
                )
            }
    }
}
