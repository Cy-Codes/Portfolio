package codes.cy.portfolio.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentifierValidationTest {
    @Test
    fun `validates safe lowercase hyphenated slugs`() {
        assertTrue("ktor-4b-architecture".isSafeSlug())
        assertTrue("v1".isSafeSlug())
        assertFalse("".isSafeSlug())
        assertFalse("UpperCase".isSafeSlug())
        assertFalse("bad_underscore".isSafeSlug())
        assertFalse("leading-".isSafeSlug())
        assertFalse("../escape".isSafeSlug())
    }

    @Test
    fun `validates SQL identifiers and sort field allowlists`() {
        assertTrue("updated_at".isSafeSqlIdentifier())
        assertFalse("updated_at; DROP TABLE".isSafeSqlIdentifier())

        val allowed = setOf("sort_order", "updated_at", "title")
        assertNull(null.validateAllowedSortField(allowed))
        assertNull("   ".validateAllowedSortField(allowed))
        assertEquals("updated_at", " updated_at ".validateAllowedSortField(allowed))
        assertFailsWith<IllegalArgumentException> {
            "unknown_col".validateAllowedSortField(allowed)
        }
    }
}
