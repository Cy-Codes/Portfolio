package codes.cy.portfolio.presentation

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.serialization.PortfolioJson
import codes.cy.portfolio.presentation.data.ApiResult
import codes.cy.portfolio.presentation.data.KtorPublicPortfolioApi
import codes.cy.portfolio.presentation.data.PortfolioHttpClientFactory
import codes.cy.portfolio.presentation.data.toDisplayMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class KtorPublicPortfolioApiTest {
    private val fixedInstant: Instant = Instant.parse("2026-11-20T12:00:00Z")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `decodes portfolio overview projects writeups documents and analytics acceptance`() = runTest {
        val overview = PortfolioOverviewDto(
            profile = ProfileDto(
                fullName = "CY",
                headline = "Staff Engineer",
                bioMarkdown = "Bio",
                updatedAt = fixedInstant,
            ),
        )
        val projectPage = PagedResult.of(
            items = listOf(
                ProjectSummaryDto(
                    id = "1",
                    slug = "kmp-portfolio",
                    title = "Portfolio",
                    summary = "Summary",
                    status = ProjectStatus.ACTIVE,
                    isFeatured = true,
                    sortOrder = 1,
                    updatedAt = fixedInstant,
                ),
            ),
            totalCount = 1L,
            options = QueryOptions.DEFAULT,
        )
        val projectDetail = ProjectDetailDto(
            id = "1",
            slug = "kmp-portfolio",
            title = "Portfolio",
            summary = "Summary",
            caseStudyMarkdown = "# Detail",
            status = ProjectStatus.ACTIVE,
            isFeatured = true,
            sortOrder = 1,
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        )
        val writeupPage = PagedResult.of(
            items = listOf(
                WriteupSummaryDto(
                    id = "2",
                    slug = "decompose-guide",
                    title = "Decompose Guide",
                    summary = "Summary",
                    tags = listOf("kotlin"),
                    status = WriteupStatus.PUBLISHED,
                    readingTimeMin = 5,
                    publishedAt = fixedInstant,
                    updatedAt = fixedInstant,
                ),
            ),
            totalCount = 1L,
            options = QueryOptions.DEFAULT,
        )
        val writeupDetail = WriteupDetailDto(
            id = "2",
            slug = "decompose-guide",
            title = "Decompose Guide",
            summary = "Summary",
            bodyMarkdown = "# Intro",
            tags = listOf("kotlin"),
            status = WriteupStatus.PUBLISHED,
            readingTimeMin = 5,
            publishedAt = fixedInstant,
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        )
        val docsPage = PagedResult.of(
            items = listOf(
                DocumentDto(
                    id = "3",
                    title = "Resume",
                    category = DocumentCategory.RESUME,
                    originalFilename = "resume.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 100L,
                    sha256 = "a".repeat(64),
                    storageKey = "documents/3/resume.pdf",
                    isPublic = true,
                    createdAt = fixedInstant,
                ),
            ),
            totalCount = 1L,
            options = QueryOptions.DEFAULT,
        )

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/public/portfolio" -> respond(
                    content = PortfolioJson.encodeToString(overview),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/api/v1/public/projects" -> {
                    assertEquals("true", request.url.parameters["featured"])
                    assertEquals("Kotlin", request.url.parameters["skill"])
                    respond(
                        content = PortfolioJson.encodeToString(projectPage),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
                "/api/v1/public/projects/kmp-portfolio" -> respond(
                    content = PortfolioJson.encodeToString(projectDetail),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/api/v1/public/writeups" -> {
                    assertEquals("kotlin", request.url.parameters["tag"])
                    assertEquals("decompose", request.url.parameters["q"])
                    respond(
                        content = PortfolioJson.encodeToString(writeupPage),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
                "/api/v1/public/writeups/decompose-guide" -> respond(
                    content = PortfolioJson.encodeToString(writeupDetail),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/api/v1/public/documents" -> respond(
                    content = PortfolioJson.encodeToString(docsPage),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/api/v1/public/events" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    respond(content = "", status = HttpStatusCode.Accepted)
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val client = PortfolioHttpClientFactory.create(baseUrl = "https://portfolio.example.test", engine = engine)
        val api = KtorPublicPortfolioApi(client, baseUrl = "https://portfolio.example.test/")

        val portfolioRes = api.getPortfolioOverview()
        assertIs<ApiResult.Success<PortfolioOverviewDto>>(portfolioRes)
        assertEquals("CY", portfolioRes.value.profile?.fullName)

        val projectsRes = api.getProjects(featuredOnly = true, skillName = "Kotlin")
        assertIs<ApiResult.Success<PagedResult<ProjectSummaryDto>>>(projectsRes)
        assertEquals(1, projectsRes.value.items.size)

        val projectRes = api.getProjectBySlug("kmp-portfolio")
        assertIs<ApiResult.Success<ProjectDetailDto>>(projectRes)

        val writeupsRes = api.getWriteups(tag = "kotlin", searchQuery = "decompose")
        assertIs<ApiResult.Success<PagedResult<WriteupSummaryDto>>>(writeupsRes)

        val writeupRes = api.getWriteupBySlug("decompose-guide")
        assertIs<ApiResult.Success<WriteupDetailDto>>(writeupRes)

        val docsRes = api.getDocuments()
        assertIs<ApiResult.Success<PagedResult<DocumentDto>>>(docsRes)

        val eventRes = api.recordEvent(
            TrackEventRequest(
                eventType = AnalyticsEventType.PAGE_VIEW,
                path = "/",
                platform = ClientPlatform.WEB,
                viewportTier = ViewportTier.LARGE,
            ),
        )
        assertIs<ApiResult.Success<Unit>>(eventRes)

        assertEquals(
            "https://portfolio.example.test/api/v1/public/documents/doc-1/download",
            api.buildDocumentDownloadUrl("doc-1"),
        )
        assertEquals(
            "https://portfolio.example.test/read/decompose-guide",
            api.buildReaderHtmlUrl("decompose-guide"),
        )
    }

    @Test
    fun `decodes all sealed ApiError variants cleanly and falls back when non-JSON returned`() = runTest {
        val errors: List<Pair<HttpStatusCode, ApiError>> = listOf(
            HttpStatusCode.BadRequest to ApiError.ValidationError(
                message = "Invalid query",
                fieldErrors = listOf(FieldViolation("page", "Out of bounds")),
            ),
            HttpStatusCode.Unauthorized to ApiError.Unauthorized("Missing token"),
            HttpStatusCode.Forbidden to ApiError.Forbidden("Denied"),
            HttpStatusCode.NotFound to ApiError.NotFound("Not found", "writeup", "missing-slug"),
            HttpStatusCode.Conflict to ApiError.Conflict("Duplicate slug", "uk_slug"),
            HttpStatusCode.InternalServerError to ApiError.InternalError(ApiError.GENERIC_INTERNAL_MESSAGE, "trace-9"),
        )

        var callIndex = 0
        val engine = MockEngine { _ ->
            if (callIndex < errors.size) {
                val (status, apiError) = errors[callIndex++]
                respond(
                    content = PortfolioJson.encodeToString(ApiError.serializer(), apiError),
                    status = status,
                    headers = jsonHeaders,
                )
            } else {
                respond(
                    content = "Bad Gateway HTML",
                    status = HttpStatusCode.BadGateway,
                )
            }
        }

        val client = PortfolioHttpClientFactory.create(baseUrl = "https://portfolio.example.test", engine = engine)
        val api = KtorPublicPortfolioApi(client, baseUrl = "https://portfolio.example.test")

        errors.forEach { (_, expected) ->
            val failure = assertIs<ApiResult.Failure>(api.getPortfolioOverview())
            assertEquals(expected, failure.error)
            assertTrue(failure.error.toDisplayMessage().isNotBlank())
        }

        val fallbackFailure = assertIs<ApiResult.Failure>(api.getPortfolioOverview())
        assertIs<ApiError.InternalError>(fallbackFailure.error)
    }
}
