package codes.cy.portfolio.core.pagination

import codes.cy.portfolio.core.model.SortDirection
import kotlinx.serialization.Serializable

/**
 * Immutable query options governing stateless offset pagination, sorting, and filtering.
 */
@Serializable
data class QueryOptions(
    val page: Int = DEFAULT_PAGE,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val sortBy: String? = null,
    val sortDirection: SortDirection = DEFAULT_SORT_DIRECTION,
    val filters: Map<String, String?> = emptyMap(),
) {
    val normalizedPage: Int
        get() = if (page < DEFAULT_PAGE) DEFAULT_PAGE else page

    val normalizedPageSize: Int
        get() = when {
            pageSize < MIN_PAGE_SIZE -> DEFAULT_PAGE_SIZE
            pageSize > MAX_PAGE_SIZE -> MAX_PAGE_SIZE
            else -> pageSize
        }

    /**
     * Pure calculation returning the SQL offset guarded against negative values and
     * 32-bit signed integer overflow (CWE-190). Returns `null` when out of bounds.
     */
    fun calculateOffsetOrNull(): Long? {
        if (page < DEFAULT_PAGE || pageSize < MIN_PAGE_SIZE) {
            return null
        }
        val zeroBasedPage = page.toLong() - DEFAULT_PAGE.toLong()
        val effectivePageSize = normalizedPageSize.toLong()
        if (zeroBasedPage > Int.MAX_VALUE.toLong() / effectivePageSize) {
            return null
        }
        val offset = zeroBasedPage * effectivePageSize
        return if (offset <= Int.MAX_VALUE.toLong()) offset else null
    }

    companion object {
        const val DEFAULT_PAGE: Int = 1
        const val MIN_PAGE_SIZE: Int = 1
        const val DEFAULT_PAGE_SIZE: Int = 20
        const val MAX_PAGE_SIZE: Int = 100
        val DEFAULT_SORT_DIRECTION: SortDirection = SortDirection.ASC
        val DEFAULT: QueryOptions = QueryOptions()
    }
}
