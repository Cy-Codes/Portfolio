package codes.cy.portfolio.server.db.errors

import codes.cy.portfolio.core.error.FieldViolation

/**
 * Typed domain and boundary exception hierarchy mapped cleanly to [codes.cy.portfolio.core.error.ApiError].
 */
sealed class PortfolioException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ValidationException(
    message: String,
    val fieldErrors: List<FieldViolation> = emptyList(),
) : PortfolioException(message)

class UnauthorizedException(
    message: String = "Authentication credentials are missing or invalid.",
) : PortfolioException(message)

class ForbiddenException(
    message: String = "You do not have permission to access this resource.",
) : PortfolioException(message)

class NotFoundException(
    val resource: String,
    val identifier: String,
    message: String = "$resource '$identifier' was not found.",
) : PortfolioException(message)

class ConflictException(
    message: String,
    val constraint: String? = null,
    cause: Throwable? = null,
) : PortfolioException(message, cause)
