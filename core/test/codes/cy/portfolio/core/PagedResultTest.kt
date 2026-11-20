package codes.cy.portfolio.core.pagination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagedResultTest {
    @Test
    fun `computes totalPages and navigation flags accurately`() {
        val middlePage = PagedResult.of(
            items = listOf("a", "b"),
            totalCount = 45L,
            options = QueryOptions(page = 2, pageSize = 20),
        )
        assertEquals(3, middlePage.totalPages)
        assertTrue(middlePage.hasPreviousPage)
        assertTrue(middlePage.hasNextPage)

        val lastPage = PagedResult.of(
            items = listOf("z"),
            totalCount = 45L,
            options = QueryOptions(page = 3, pageSize = 20),
        )
        assertTrue(lastPage.hasPreviousPage)
        assertFalse(lastPage.hasNextPage)
    }

    @Test
    fun `handles zero totalCount cleanly`() {
        val empty = PagedResult.of(
            items = emptyList<String>(),
            totalCount = 0L,
            options = QueryOptions.DEFAULT,
        )
        assertEquals(0, empty.totalPages)
        assertFalse(empty.hasPreviousPage)
        assertFalse(empty.hasNextPage)
    }
}
