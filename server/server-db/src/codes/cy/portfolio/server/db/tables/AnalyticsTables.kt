package codes.cy.portfolio.server.db.tables

import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.ViewportTier
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

private const val ENUM_COLUMN_LENGTH: Int = 32
private const val VISITOR_HASH_LENGTH: Int = 64

object AnalyticsEventsTable : Table("analytics.events") {
    val id: Column<Long> = long("id").autoIncrement()
    val eventType: Column<AnalyticsEventType> = enumerationByName(
        "event_type",
        ENUM_COLUMN_LENGTH,
        AnalyticsEventType::class,
    )
    val path: Column<String> = text("path")
    val entityId: Column<Uuid?> = uuid("entity_id").nullable()
    val referrerHost: Column<String?> = text("referrer_host").nullable()
    val platform: Column<ClientPlatform> = enumerationByName(
        "platform",
        ENUM_COLUMN_LENGTH,
        ClientPlatform::class,
    )
    val viewportTier: Column<ViewportTier> = enumerationByName(
        "viewport_tier",
        ENUM_COLUMN_LENGTH,
        ViewportTier::class,
    )
    val visitorHash: Column<String> = varchar("visitor_hash", VISITOR_HASH_LENGTH)
    val createdAt: Column<OffsetDateTime> = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object AnalyticsDailyRollupsTable : Table("analytics.daily_rollups") {
    val day: Column<LocalDate> = date("day")
    val eventType: Column<AnalyticsEventType> = enumerationByName(
        "event_type",
        ENUM_COLUMN_LENGTH,
        AnalyticsEventType::class,
    )
    val path: Column<String> = text("path")
    val platform: Column<ClientPlatform> = enumerationByName(
        "platform",
        ENUM_COLUMN_LENGTH,
        ClientPlatform::class,
    )
    val viewportTier: Column<ViewportTier> = enumerationByName(
        "viewport_tier",
        ENUM_COLUMN_LENGTH,
        ViewportTier::class,
    )
    val eventCount: Column<Long> = long("event_count")
    val uniqueVisitors: Column<Long> = long("unique_visitors")

    override val primaryKey: PrimaryKey = PrimaryKey(day, eventType, path, platform, viewportTier)
}
