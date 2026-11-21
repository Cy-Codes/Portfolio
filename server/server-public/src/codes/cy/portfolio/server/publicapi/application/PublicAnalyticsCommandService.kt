package codes.cy.portfolio.server.publicapi.application

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.repositories.AnalyticsCommandRepository
import codes.cy.portfolio.server.publicapi.domain.VisitorHashGenerator
import java.time.Clock
import java.time.ZoneOffset
import kotlin.uuid.Uuid

/**
 * Side-effecting command service for cookieless first-party analytics ingestion via `POST /events`.
 */
class PublicAnalyticsCommandService(
    private val analyticsRepo: AnalyticsCommandRepository,
    private val analyticsSalt: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun recordEvent(
        request: TrackEventRequest,
        clientIp: String,
        userAgent: String,
    ) {
        val normalizedPath = request.path.trim()
        val violations = buildList {
            if (normalizedPath.isEmpty() || !normalizedPath.startsWith('/') || normalizedPath.length > MAX_PATH_LENGTH) {
                add(FieldViolation(field = "path", message = "Path must start with '/' and be <= $MAX_PATH_LENGTH chars."))
            }
            val rawEntityId = request.entityId
            if (rawEntityId != null) {
                val parsedOk = runCatching { Uuid.parse(rawEntityId.trim()) }.isSuccess
                if (!parsedOk) {
                    add(FieldViolation(field = "entityId", message = "entityId must be a valid UUID when provided."))
                }
            }
            val cleanHost = request.referrerHost?.trim()
            if (cleanHost != null && cleanHost.length > MAX_HOST_LENGTH) {
                add(FieldViolation(field = "referrerHost", message = "referrerHost must be <= $MAX_HOST_LENGTH chars."))
            }
        }
        if (violations.isNotEmpty()) {
            throw ValidationException(
                message = "Invalid analytics event payload.",
                fieldErrors = violations,
            )
        }

        val sanitizedRequest = request.copy(
            path = normalizedPath,
            entityId = request.entityId?.trim()?.takeIf { it.isNotEmpty() },
            referrerHost = request.referrerHost?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        )
        val utcDate = clock.instant().atOffset(ZoneOffset.UTC).toLocalDate()
        val visitorHash = VisitorHashGenerator.computeVisitorHash(
            dateUtc = utcDate,
            salt = analyticsSalt,
            clientIp = clientIp,
            userAgent = userAgent,
        )
        analyticsRepo.recordEvent(sanitizedRequest, visitorHash)
    }

    companion object {
        private const val MAX_PATH_LENGTH: Int = 512
        private const val MAX_HOST_LENGTH: Int = 255
    }
}
