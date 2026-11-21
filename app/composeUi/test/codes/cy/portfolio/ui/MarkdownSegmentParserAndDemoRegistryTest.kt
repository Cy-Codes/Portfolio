package codes.cy.portfolio.ui

import codes.cy.portfolio.ui.demos.DemoRegistry
import codes.cy.portfolio.ui.markdown.MarkdownSegment
import codes.cy.portfolio.ui.markdown.MarkdownSegmentParser
import codes.cy.portfolio.ui.platform.formatByteSize
import codes.cy.portfolio.ui.platform.formatMonthYear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class MarkdownSegmentParserAndDemoRegistryTest {

    @Test
    fun `MarkdownSegmentParser separates demo islands while preserving standard fenced code blocks`() {
        val markdown = """
            # Architecture Overview

            ```kotlin
            val options = QueryOptions(page = 1, pageSize = 20)
            // ```demo:ignored-inside-regular-fence
            ```

            ```demo:adaptive-breakpoints
            ```

            ```sql
            SELECT id FROM portfolio.projects;
            ```
        """.trimIndent()

        val segments = MarkdownSegmentParser.parse(markdown)
        assertEquals(3, segments.size)

        val firstProse = segments[0] as MarkdownSegment.Prose
        assertTrue(firstProse.markdown.contains("QueryOptions"))

        val demoSegment = segments[1] as MarkdownSegment.DemoIsland
        assertEquals("adaptive-breakpoints", demoSegment.demoId)

        val trailingProse = segments[2] as MarkdownSegment.Prose
        assertTrue(trailingProse.markdown.contains("SELECT id FROM portfolio.projects"))
    }

    @Test
    fun `DemoRegistry resolves all built-in portfolio demos and rejects unregistered IDs`() {
        assertNotNull(DemoRegistry.resolve("adaptive-breakpoints"))
        assertNotNull(DemoRegistry.resolve("state-flow-udf"))
        assertNotNull(DemoRegistry.resolve("pagination-guard"))
        assertNull(DemoRegistry.resolve("unknown-demo-widget"))
    }

    @Test
    fun `formatting helpers render human readable sizes and month year dates`() {
        assertEquals("512 B", 512L.formatByteSize())
        assertEquals("1.5 KB", 1536L.formatByteSize())
        assertEquals("2.0 MB", (2L * 1024L * 1024L).formatByteSize())
        assertEquals("Mar 2024", LocalDate.parse("2024-03-01").formatMonthYear())
        assertEquals("Nov 2026", LocalDate.parse("2026-11-21").formatMonthYear())
    }
}
