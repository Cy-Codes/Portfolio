package codes.cy.portfolio.server.admin.api

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.SortDirection
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.routes.AdminApi
import codes.cy.portfolio.server.db.errors.ValidationException

fun AdminApi.Projects.toValidatedQueryOptions(): QueryOptions =
    validatePageBounds(page = page, pageSize = pageSize)

fun AdminApi.Writeups.toValidatedQueryOptions(): QueryOptions =
    validatePageBounds(page = page, pageSize = pageSize, defaultDirection = SortDirection.DESC)

fun AdminApi.Documents.toValidatedQueryOptions(): QueryOptions =
    validatePageBounds(page = page, pageSize = pageSize, defaultDirection = SortDirection.DESC)

private fun validatePageBounds(
    page: Int,
    pageSize: Int,
    defaultDirection: SortDirection = QueryOptions.DEFAULT_SORT_DIRECTION,
): QueryOptions {
    val options = QueryOptions(
        page = page,
        pageSize = pageSize,
        sortDirection = defaultDirection,
    )
    if (options.calculateOffsetOrNull() == null) {
        throw ValidationException(
            message = "Pagination parameters out of range: page=$page, pageSize=$pageSize.",
            fieldErrors = listOf(
                FieldViolation("page", "Page and pageSize must produce a positive 32-bit SQL offset."),
            ),
        )
    }
    return options
}
