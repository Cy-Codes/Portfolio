package codes.cy.portfolio.server.db.errors

import codes.cy.portfolio.core.error.ApiError
import java.sql.SQLException
import org.postgresql.util.PSQLException

private const val PG_UNIQUE_VIOLATION: String = "23505"
private const val PG_FOREIGN_KEY_VIOLATION: String = "23503"
private const val PG_CHECK_VIOLATION: String = "23514"
private const val PG_INVALID_TEXT_REPRESENTATION: String = "22P02"

/**
 * Pure exception-to-contract mapper converting domain exceptions, PostgreSQL integrity errors,
 * and unexpected failures into sealed [ApiError] variants without leaking sensitive internals.
 */
fun Throwable.toApiError(
    isDevelopment: Boolean,
    traceId: String? = null,
): ApiError =
    when (this) {
        is ValidationException -> ApiError.ValidationError(
            message = message ?: "Validation failed.",
            fieldErrors = fieldErrors,
        )
        is UnauthorizedException -> ApiError.Unauthorized(
            message = message ?: "Authentication required.",
        )
        is ForbiddenException -> ApiError.Forbidden(
            message = message ?: "Access denied.",
        )
        is NotFoundException -> ApiError.NotFound(
            message = message ?: "$resource '$identifier' was not found.",
            resource = resource,
            identifier = identifier,
        )
        is ConflictException -> ApiError.Conflict(
            message = message ?: "Resource conflict.",
            constraint = constraint,
        )
        is IllegalArgumentException -> ApiError.ValidationError(
            message = message ?: "Invalid request parameter.",
        )
        else -> mapCausalSqlOrInternalError(isDevelopment = isDevelopment, traceId = traceId)
    }

private fun Throwable.mapCausalSqlOrInternalError(
    isDevelopment: Boolean,
    traceId: String?,
): ApiError {
    val sqlException = findCausalSqlException()
    val constraintName = (sqlException as? PSQLException)?.serverErrorMessage?.constraint
    return when (sqlException?.sqlState) {
        PG_UNIQUE_VIOLATION -> ApiError.Conflict(
            message = "A conflicting resource with the same unique key already exists.",
            constraint = constraintName,
        )
        PG_FOREIGN_KEY_VIOLATION -> ApiError.Conflict(
            message = "Referenced resource does not exist or is still referenced.",
            constraint = constraintName,
        )
        PG_CHECK_VIOLATION -> ApiError.ValidationError(
            message = if (isDevelopment) {
                sqlException.message ?: "Request violates database validation constraint."
            } else {
                "Request violates validation constraints."
            },
        )
        PG_INVALID_TEXT_REPRESENTATION -> ApiError.ValidationError(
            message = "Malformed identifier or input format.",
        )
        else -> ApiError.InternalError(
            message = if (isDevelopment) {
                message ?: ApiError.GENERIC_INTERNAL_MESSAGE
            } else {
                ApiError.GENERIC_INTERNAL_MESSAGE
            },
            traceId = traceId,
        )
    }
}

private fun Throwable.findCausalSqlException(): SQLException? =
    generateSequence(this) { it.cause }
        .filterIsInstance<SQLException>()
        .firstOrNull()
