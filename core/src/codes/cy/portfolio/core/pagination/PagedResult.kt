package codes.cy.portfolio.core.pagination

import kotlinx.serialization.Serializable

@Serializable
data class PagedResult<T>(
    val items: List<T>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
) {
    companion object {
        private const val EMPTY_COUNT: Long = 0L
        private const val FIRST_PAGE: Int = 1

        fun <T> of(
            items: List<T>,
            totalCount: Long,
            options: QueryOptions,
        ): PagedResult<T> {
            val sanitizedTotal = if (totalCount < EMPTY_COUNT) EMPTY_COUNT else totalCount
            val effectivePageSize = options.normalizedPageSize
            val effectivePage = options.normalizedPage
            val computedTotalPages = if (sanitizedTotal == EMPTY_COUNT) {
                0
            } else {
                ((sanitizedTotal + effectivePageSize - 1L) / effectivePageSize).toInt()
            }
            return PagedResult(
                items = items,
                totalCount = sanitizedTotal,
                page = effectivePage,
                pageSize = effectivePageSize,
                totalPages = computedTotalPages,
                hasPreviousPage = effectivePage > FIRST_PAGE,
                hasNextPage = effectivePage < computedTotalPages,
            )
        }
    }
}
