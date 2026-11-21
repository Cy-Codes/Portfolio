package codes.cy.portfolio.server.publicapi.api

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.SortDirection
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.routes.PublicApi
import codes.cy.portfolio.core.validation.validateAllowedSortField
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.repositories.ExposedProjectRepository
import codes.cy.portfolio.server.db.repositories.ExposedWriteupRepository

fun PublicApi.Projects.toValidatedQueryOptions(): QueryOptions =
    buildValidatedQueryOptions(
        page = page,
        pageSize = pageSize,
        sortBy = sortBy,
        sortDirection = sortDirection,
        allowedSortFields = ExposedProjectRepository.ALLOWED_SORT_FIELDS,
    )

fun PublicApi.Writeups.toValidatedQueryOptions(): QueryOptions =
    buildValidatedQueryOptions(
        page = page,
        pageSize = pageSize,
        sortBy = sortBy,
        sortDirection = sortDirection,
        allowedSortFields = ExposedWriteupRepository.ALLOWED_SORT_FIELDS,
    )

private fun buildValidatedQueryOptions(
    page: Int,
    pageSize: Int,
    sortBy: String?,
    sortDirection: String?,
    allowedSortFields: Set<String>,
): QueryOptions {
    val parsedDirection = runCatching { SortDirection.parse(sortDirection) }.getOrElse { ex ->
        throw ValidationException(
            message = ex.message ?: "Invalid sortDirection '$sortDirection'.",
            fieldErrors = listOf(FieldViolation(field = "sortDirection", message = "Expected 'asc' or 'desc'.")),
        )
    }
    val validatedSortBy = runCatching { sortBy.validateAllowedSortField(allowedSortFields) }.getOrElse { ex ->
        throw ValidationException(
            message = ex.message ?: "Unsupported sortBy '$sortBy'.",
            fieldErrors = listOf(FieldViolation(field = "sortBy", message = ex.message ?: "Invalid sort field.")),
        )
    }
    val options = QueryOptions(
        page = page,
        pageSize = pageSize,
        sortBy = validatedSortBy,
        sortDirection = parsedDirection,
    )
    if (options.calculateOffsetOrNull() == null) {
        throw ValidationException(
            message = "Pagination parameters out of range: page=$page, pageSize=$pageSize.",
            fieldErrors = listOf(
                FieldViolation(
                    field = "page",
                    message = "Page and pageSize must produce a positive 32-bit SQL offset.",
                ),
            ),
        )
    }
    return options
}
