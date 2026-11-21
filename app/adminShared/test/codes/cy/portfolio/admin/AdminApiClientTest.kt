package codes.cy.portfolio.admin

import codes.cy.portfolio.admin.crypto.sha256Hex
import codes.cy.portfolio.admin.data.AdminApiResult
import codes.cy.portfolio.admin.data.AdminAuthCredentials
import codes.cy.portfolio.admin.data.AdminAuthMode
import codes.cy.portfolio.admin.data.AdminHttpClientFactory
import codes.cy.portfolio.admin.data.AdminSessionConfigHolder
import codes.cy.portfolio.admin.data.KtorAdminApiClient
import codes.cy.portfolio.admin.data.toAdminDisplayMessage
import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.AdminSessionDto
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.PresignUploadRequest
import codes.cy.portfolio.core.model.PresignUploadResponse
import codes.cy.portfolio.core.model.StatsOverviewDto
import codes.cy.portfolio.core.model.StatsTimeRange
import codes.cy.portfolio.core.serialization.PortfolioJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class AdminApiClientTest {

    @Test
    fun `api client routes Bearer and Cloudflare Access headers dynamically and parses overview`() = runTest {
        val observedHeaders = mutableListOf<Pair<String?, String?>>()
        val observedPaths = mutableListOf<String>()
        val holder = AdminSessionConfigHolder(
            initialBaseUrl = "http://127.0.0.1:8081",
            initialCredentials = AdminAuthCredentials(AdminAuthMode.DEV_BEARER_TOKEN, "dev-jwt-123"),
        )

        val apiEngine = MockEngine { request ->
            observedPaths.add("${request.method.value} ${request.url.encodedPath}?${request.url.encodedQuery}")
            observedHeaders.add(
                request.headers[HttpHeaders.Authorization] to
                    request.headers[AdminHttpClientFactory.HEADER_CF_ACCESS_JWT],
            )
            when (request.url.encodedPath) {
                "/api/v1/admin/auth/session" -> respond(
                    content = PortfolioJson.encodeToString(
                        AdminSessionDto.serializer(),
                        AdminSessionDto(subject = "dev-owner", role = "owner", authSource = "dev-jwt"),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                "/api/v1/admin/stats/overview" -> respond(
                    content = PortfolioJson.encodeToString(
                        StatsOverviewDto.serializer(),
                        StatsOverviewDto(
                            range = StatsTimeRange.SEVEN_DAYS,
                            totalEvents = 120L,
                            uniqueVisitors = 42L,
                            pageViews = 80L,
                            writeupReads = 25L,
                            projectClicks = 10L,
                            documentDownloads = 5L,
                        ),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }

        val client = KtorAdminApiClient(
            apiHttpClient = AdminHttpClientFactory.createApiClient(holder, apiEngine),
            binaryUploadClient = AdminHttpClientFactory.createBinaryUploadClient(MockEngine { respond("", HttpStatusCode.OK) }),
        )

        val sessionRes = assertIs<AdminApiResult.Success<AdminSessionDto>>(client.getSession())
        assertEquals("dev-owner", sessionRes.value.subject)
        assertEquals("Bearer dev-jwt-123" to null, observedHeaders[0])

        holder.update(
            baseUrl = "https://admin.example.test",
            credentials = AdminAuthCredentials(AdminAuthMode.CLOUDFLARE_ACCESS_ASSERTION, "cf-assertion-xyz"),
        )
        val statsRes = assertIs<AdminApiResult.Success<StatsOverviewDto>>(
            client.getStatsOverview(StatsTimeRange.SEVEN_DAYS),
        )
        assertEquals(StatsTimeRange.SEVEN_DAYS, statsRes.value.range)
        assertEquals(120L, statsRes.value.totalEvents)
        assertEquals(null to "cf-assertion-xyz", observedHeaders[1])
        assertTrue(observedPaths[1].contains("range=7d"))
    }

    @Test
    fun `presigned binary PUT sends raw bytes without leaking admin authorization headers`() = runTest {
        val now = Instant.parse("2026-11-21T00:00:00Z")
        val pdfBytes = "%PDF-1.7\nbinary-resume".encodeToByteArray()
        val digest = pdfBytes.sha256Hex()
        val holder = AdminSessionConfigHolder(
            initialBaseUrl = "http://127.0.0.1:8081",
            initialCredentials = AdminAuthCredentials(AdminAuthMode.DEV_BEARER_TOKEN, "secret-admin-jwt"),
        )

        var storageMethod: HttpMethod? = null
        var storageAuthHeader: String? = "UNSET"
        var storageCfHeader: String? = "UNSET"
        var storageHostHeader: String? = null
        var storageCustomHeader: String? = null
        var storageBodyBytes: ByteArray = ByteArray(0)

        val apiEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/admin/documents/presign" -> respond(
                    content = PortfolioJson.encodeToString(
                        PresignUploadResponse.serializer(),
                        PresignUploadResponse(
                            documentId = "80000000-0000-0000-0000-000000000001",
                            storageKey = "documents/80000000-0000-0000-0000-000000000001/resume.pdf",
                            presignedPutUrl = "http://127.0.0.1:9000/portfolio-assets/upload-target?sig=abc",
                            requiredHeaders = mapOf(
                                "Host" to "127.0.0.1:9000",
                                "Content-Type" to "application/pdf",
                                "x-amz-meta-sha256" to digest,
                            ),
                            expiresAt = now,
                        ),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                "/api/v1/admin/documents/confirm" -> respond(
                    content = PortfolioJson.encodeToString(
                        DocumentDto.serializer(),
                        DocumentDto(
                            id = "80000000-0000-0000-0000-000000000001",
                            title = "Resume 2026",
                            category = DocumentCategory.RESUME,
                            originalFilename = "resume.pdf",
                            mimeType = "application/pdf",
                            sizeBytes = pdfBytes.size.toLong(),
                            sha256 = digest,
                            storageKey = "documents/80000000-0000-0000-0000-000000000001/resume.pdf",
                            isPublic = true,
                            createdAt = now,
                        ),
                    ),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }

        val uploadEngine = MockEngine { request ->
            storageMethod = request.method
            storageAuthHeader = request.headers[HttpHeaders.Authorization]
            storageCfHeader = request.headers[AdminHttpClientFactory.HEADER_CF_ACCESS_JWT]
            storageHostHeader = request.headers[HttpHeaders.Host]
            storageCustomHeader = request.headers["x-amz-meta-sha256"]
            storageBodyBytes = request.body.toByteArray()
            respond("", HttpStatusCode.OK)
        }

        val client = KtorAdminApiClient(
            apiHttpClient = AdminHttpClientFactory.createApiClient(holder, apiEngine),
            binaryUploadClient = AdminHttpClientFactory.createBinaryUploadClient(uploadEngine),
        )

        val presign = assertIs<AdminApiResult.Success<PresignUploadResponse>>(
            client.presignDocumentUpload(
                PresignUploadRequest(
                    originalFilename = "resume.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = pdfBytes.size.toLong(),
                    sha256 = digest,
                ),
            ),
        ).value

        val putRes = client.uploadBinaryToPresignedUrl(
            presignedPutUrl = presign.presignedPutUrl,
            requiredHeaders = presign.requiredHeaders,
            mimeType = "application/pdf",
            bytes = pdfBytes,
        )
        assertIs<AdminApiResult.Success<Unit>>(putRes)
        assertEquals(HttpMethod.Put, storageMethod)
        assertNull(storageAuthHeader)
        assertNull(storageCfHeader)
        assertNull(storageHostHeader)
        assertEquals(digest, storageCustomHeader)
        assertTrue(pdfBytes.contentEquals(storageBodyBytes))
        assertFalse(storageBodyBytes.isEmpty())

        val confirmed = assertIs<AdminApiResult.Success<DocumentDto>>(
            client.confirmDocumentUpload(
                ConfirmUploadRequest(
                    documentId = presign.documentId,
                    storageKey = presign.storageKey,
                    title = "Resume 2026",
                    category = DocumentCategory.RESUME,
                    originalFilename = "resume.pdf",
                    declaredMimeType = "application/pdf",
                    sha256 = digest,
                    isPublic = true,
                ),
            ),
        ).value
        assertEquals("Resume 2026", confirmed.title)
    }

    @Test
    fun `api client decodes structured ApiError variants and formats field violations`() = runTest {
        val holder = AdminSessionConfigHolder("http://127.0.0.1:8081")
        val validationError = ApiError.ValidationError(
            message = "Invalid writeup payload.",
            fieldErrors = listOf(FieldViolation("slug", "Slug must be lowercase hyphenated.")),
        )
        val mockEngine = MockEngine {
            respond(
                content = PortfolioJson.encodeToString(ApiError.serializer(), validationError),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = KtorAdminApiClient(
            apiHttpClient = AdminHttpClientFactory.createApiClient(holder, mockEngine),
            binaryUploadClient = AdminHttpClientFactory.createBinaryUploadClient(MockEngine { respond("", HttpStatusCode.OK) }),
        )

        val failure = assertIs<AdminApiResult.Failure>(client.getSession())
        val decoded = assertIs<ApiError.ValidationError>(failure.error)
        assertEquals("slug", decoded.fieldErrors.single().field)
        val display = decoded.toAdminDisplayMessage()
        assertTrue(display.contains("Invalid writeup payload."))
        assertTrue(display.contains("slug: Slug must be lowercase hyphenated."))
    }
}
