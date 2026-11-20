package codes.cy.portfolio.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackEventRequest(
    val eventType: AnalyticsEventType,
    val path: String,
    val entityId: String? = null,
    val referrerHost: String? = null,
    val platform: ClientPlatform,
    val viewportTier: ViewportTier,
)

@Serializable
data class NamedCountDto(
    val name: String,
    val count: Long,
)

@Serializable
data class PathStatsDto(
    val path: String,
    val eventType: AnalyticsEventType,
    val eventCount: Long,
    val uniqueVisitors: Long,
)

@Serializable
data class StatsOverviewDto(
    val range: StatsTimeRange,
    val totalEvents: Long,
    val uniqueVisitors: Long,
    val pageViews: Long,
    val writeupReads: Long,
    val projectClicks: Long,
    val documentDownloads: Long,
    val topPaths: List<PathStatsDto> = emptyList(),
    val topReferrers: List<NamedCountDto> = emptyList(),
    val byPlatform: List<NamedCountDto> = emptyList(),
    val byViewportTier: List<NamedCountDto> = emptyList(),
)
