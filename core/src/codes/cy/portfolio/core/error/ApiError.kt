package codes.cy.portfolio.core.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FieldViolation(
    val field: String,
    val message: String,
)

/**
 * Pure KMP client-server error contract serialized over HTTP error responses.
 */
@Serializable
sealed interface ApiError {
    val message: String

    @Serializable
    @SerialName("validation_error")
    data class ValidationError(
        override val message: String,
        val fieldErrors: List<FieldViolation> = emptyList(),
    ) : ApiError

    @Serializable
    @SerialName("unauthorized")
    data class Unauthorized(
        override val message: String,
    ) : ApiError

    @Serializable
    @SerialName("forbidden")
    data class Forbidden(
        override val message: String,
    ) : ApiError

    @Serializable
    @SerialName("not_found")
    data class NotFound(
        override val message: String,
        val resource: String,
        val identifier: String,
    ) : ApiError

    @Serializable
    @SerialName("conflict")
    data class Conflict(
        override val message: String,
        val constraint: String? = null,
    ) : ApiError

    @Serializable
    @SerialName("internal_error")
    data class InternalError(
        override val message: String,
        val traceId: String? = null,
    ) : ApiError

    companion object {
        const val HTTP_BAD_REQUEST: Int = 400
        const val HTTP_UNAUTHORIZED: Int = 401
        const val HTTP_FORBIDDEN: Int = 403
        const val HTTP_NOT_FOUND: Int = 404
        const val HTTP_CONFLICT: Int = 409
        const val HTTP_INTERNAL_SERVER_ERROR: Int = 500
        const val GENERIC_INTERNAL_MESSAGE: String = "An unexpected internal error occurred."
    }
}

/**
 * Pure mapping from sealed [ApiError] variants to canonical HTTP status codes.
 */
fun ApiError.httpStatusCode(): Int =
    when (this) {
        is ApiError.ValidationError -> ApiError.HTTP_BAD_REQUEST
        is ApiError.Unauthorized -> ApiError.HTTP_UNAUTHORIZED
        is ApiError.Forbidden -> ApiError.HTTP_FORBIDDEN
        is ApiError.NotFound -> ApiError.HTTP_NOT_FOUND
        is ApiError.Conflict -> ApiError.HTTP_CONFLICT
        is ApiError.InternalError -> ApiError.HTTP_INTERNAL_SERVER_ERROR
    }
