package codes.cy.portfolio.core.pagination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryOptionsTest {
    @Test
    fun `normalizes page and clamps pageSize bounds`() {
        assertEquals(1, QueryOptions(page = 0).normalizedPage)
        assertEquals(1, QueryOptions(page = -5).normalizedPage)
        assertEquals(QueryOptions.DEFAULT_PAGE_SIZE, QueryOptions(pageSize = 0).normalizedPageSize)
        assertEquals(QueryOptions.MAX_PAGE_SIZE, QueryOptions(pageSize = 500).normalizedPageSize)
        assertEquals(42, QueryOptions(pageSize = 42).normalizedPageSize)
    }

    @Test
    fun `calculates valid offsets and rejects negative or CWE-190 overflow bounds`() {
        assertEquals(0L, QueryOptions(page = 1, pageSize = 20).calculateOffsetOrNull())
        assertEquals(40L, QueryOptions(page = 3, pageSize = 20).calculateOffsetOrNull())

        assertNull(QueryOptions(page = 0, pageSize = 20).calculateOffsetOrNull())
        assertNull(QueryOptions(page = -1, pageSize = 20).calculateOffsetOrNull())
        assertNull(QueryOptions(page = 1, pageSize = 0).calculateOffsetOrNull())
        assertNull(QueryOptions(page = Int.MAX_VALUE, pageSize = QueryOptions.MAX_PAGE_SIZE).calculateOffsetOrNull())
        assertNull(QueryOptions(page = 21_474_838, pageSize = QueryOptions.MAX_PAGE_SIZE).calculateOffsetOrNull())
    }
}
