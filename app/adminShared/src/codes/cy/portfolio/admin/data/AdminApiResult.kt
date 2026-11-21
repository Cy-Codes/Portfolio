package codes.cy.portfolio.admin.data

import codes.cy.portfolio.core.error.ApiError

/**
 * Outcome container encoding admin API or object-storage transport failures as typed [ApiError] values.
 */
sealed interface AdminApiResult<out T> {
    data class Success<T>(val value: T) : AdminApiResult<T>
    data class Failure(val error: ApiError) : AdminApiResult<Nothing>
}

inline fun <T, R> AdminApiResult<T>.map(transform: (T) -> R): AdminApiResult<R> =
    when (this) {
        is AdminApiResult.Success -> AdminApiResult.Success(transform(value))
        is AdminApiResult.Failure -> this
    }

inline fun <T> AdminApiResult<T>.onSuccess(action: (T) -> Unit): AdminApiResult<T> =
    apply {
        if (this is AdminApiResult.Success) {
            action(value)
        }
    }

inline fun <T> AdminApiResult<T>.onFailure(action: (ApiError) -> Unit): AdminApiResult<T> =
    apply {
        if (this is AdminApiResult.Failure) {
            action(error)
        }
    }

inline fun <T, R> AdminApiResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (ApiError) -> R,
): R = when (this) {
    is AdminApiResult.Success -> onSuccess(value)
    is AdminApiResult.Failure -> onFailure(error)
}

/**
 * Exhaustive admin-facing error formatter over every sealed [ApiError] variant.
 */
fun ApiError.toAdminDisplayMessage(): String =
    when (this) {
        is ApiError.ValidationError -> {
            if (fieldErrors.isEmpty()) {
                message
            } else {
                val joinedFields = fieldErrors.joinToString("; ") { "${it.field}: ${it.message}" }
                "$message ($joinedFields)"
            }
        }
        is ApiError.Unauthorized -> "Authentication failed: $message"
        is ApiError.Forbidden -> "Access denied: $message"
        is ApiError.NotFound -> "Not found: $resource ('$identifier')."
        is ApiError.Conflict -> constraint?.let { "$message [$it]" } ?: message
        is ApiError.InternalError -> traceId?.let { "$message (Trace: $it)" } ?: message
    }
