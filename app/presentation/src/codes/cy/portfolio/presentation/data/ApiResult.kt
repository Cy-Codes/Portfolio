package codes.cy.portfolio.presentation.data

import codes.cy.portfolio.core.error.ApiError

/**
 * Pure KMP outcome container encoding server or transport failures as typed [ApiError] values.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(value))
        is ApiResult.Failure -> this
    }

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> =
    apply {
        if (this is ApiResult.Success) {
            action(value)
        }
    }

inline fun <T> ApiResult<T>.onFailure(action: (ApiError) -> Unit): ApiResult<T> =
    apply {
        if (this is ApiResult.Failure) {
            action(error)
        }
    }

inline fun <T, R> ApiResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (ApiError) -> R,
): R = when (this) {
    is ApiResult.Success -> onSuccess(value)
    is ApiResult.Failure -> onFailure(error)
}

/**
 * Exhaustive client display-message mapping over every sealed [ApiError] variant.
 */
fun ApiError.toDisplayMessage(): String =
    when (this) {
        is ApiError.ValidationError -> {
            val detail = fieldErrors.firstOrNull()?.let { "${it.field}: ${it.message}" }
            detail ?: message
        }
        is ApiError.Unauthorized -> message
        is ApiError.Forbidden -> message
        is ApiError.NotFound -> "Requested $resource ('$identifier') could not be found."
        is ApiError.Conflict -> message
        is ApiError.InternalError ->
            traceId?.let { "$message (Reference: $it)" } ?: message
    }
