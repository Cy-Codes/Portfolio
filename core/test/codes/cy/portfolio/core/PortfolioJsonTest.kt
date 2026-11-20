package codes.cy.portfolio.core.serialization

import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class PortfolioJsonTest {
    @Test
    fun `preserves default values explicit nulls and is-prefixed booleans`() {
        val dto = ProjectSummaryDto(
            id = "proj-1",
            slug = "portfolio-kmp",
            title = "Portfolio KMP",
            summary = "Cross-platform engineering portfolio",
            repoUrl = null,
            liveUrl = null,
            coverDocumentId = null,
            status = ProjectStatus.ACTIVE,
            isFeatured = true,
            skills = listOf(
                SkillDto(
                    id = "skill-1",
                    name = "Kotlin",
                    category = "Language",
                    isFeatured = false,
                    sortOrder = 1,
                ),
            ),
            sortOrder = 0,
            updatedAt = Instant.parse("2026-11-20T12:00:00Z"),
        )

        val encoded = PortfolioJson.encodeToString(dto)
        assertTrue(encoded.contains("\"isFeatured\":true"))
        assertTrue(encoded.contains("\"repoUrl\":null"))
        assertTrue(encoded.contains("\"sortOrder\":0"))

        val decoded = PortfolioJson.decodeFromString<ProjectSummaryDto>(encoded)
        assertEquals(dto, decoded)
    }

    @Test
    fun `ignores unknown keys when decoding`() {
        val raw = """
            {
              "id": "skill-1",
              "name": "PostgreSQL",
              "category": "Database",
              "isFeatured": true,
              "sortOrder": 2,
              "unexpectedFutureField": "ignored"
            }
        """.trimIndent()

        val decoded = PortfolioJson.decodeFromString<SkillDto>(raw)
        assertEquals("PostgreSQL", decoded.name)
        assertTrue(decoded.isFeatured)
    }
}
