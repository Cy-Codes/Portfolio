package codes.cy.portfolio.presentation.data

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.routes.PublicApi
import codes.cy.portfolio.core.serialization.PortfolioJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pure read-only public portfolio client contract.
 */
interface PublicPortfolioApi {
    suspend fun getPortfolioOverview(): ApiResult<PortfolioOverviewDto>

    suspend fun getProjects(
        options: QueryOptions = QueryOptions.DEFAULT,
        featuredOnly: Boolean? = null,
        skillName: String? = null,
    ): ApiResult<PagedResult<ProjectSummaryDto>>

    suspend fun getProjectBySlug(slug: String): ApiResult<ProjectDetailDto>

    suspend fun getWriteups(
        options: QueryOptions = QueryOptions.DEFAULT,
        tag: String? = null,
        searchQuery: String? = null,
    ): ApiResult<PagedResult<WriteupSummaryDto>>

    suspend fun getWriteupBySlug(slug: String): ApiResult<WriteupDetailDto>

    suspend fun getDocuments(
        options: QueryOptions = QueryOptions.DEFAULT,
    ): ApiResult<PagedResult<DocumentDto>>

    fun buildDocumentDownloadUrl(documentId: String): String

    fun buildReaderHtmlUrl(slug: String): String
}

/**
 * Side-effecting analytics event sink isolated from read queries per CQS.
 */
interface AnalyticsEventSender {
    suspend fun recordEvent(request: TrackEventRequest): ApiResult<Unit>
}

class KtorPublicPortfolioApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PublicPortfolioApi, AnalyticsEventSender {

    private val normalizedBaseUrl: String = baseUrl.trimEnd('/')

    override suspend fun getPortfolioOverview(): ApiResult<PortfolioOverviewDto> =
        executeJsonRequest {
            httpClient.get(PublicApi.Portfolio())
        }

    override suspend fun getProjects(
        options: QueryOptions,
        featuredOnly: Boolean?,
        skillName: String?,
    ): ApiResult<PagedResult<ProjectSummaryDto>> =
        executeJsonRequest {
            httpClient.get(
                PublicApi.Projects(
                    page = options.page,
                    pageSize = options.pageSize,
                    sortBy = options.sortBy,
                    sortDirection = options.sortDirection.wireValue,
                    featured = featuredOnly,
                    skill = skillName,
                ),
            )
        }

    override suspend fun getProjectBySlug(slug: String): ApiResult<ProjectDetailDto> =
        executeJsonRequest {
            httpClient.get(PublicApi.Projects.BySlug(slug = slug))
        }

    override suspend fun getWriteups(
        options: QueryOptions,
        tag: String?,
        searchQuery: String?,
    ): ApiResult<PagedResult<WriteupSummaryDto>> =
        executeJsonRequest {
            httpClient.get(
                PublicApi.Writeups(
                    page = options.page,
                    pageSize = options.pageSize,
                    sortBy = options.sortBy,
                    sortDirection = options.sortDirection.wireValue,
                    tag = tag,
                    q = searchQuery,
                ),
            )
        }

    override suspend fun getWriteupBySlug(slug: String): ApiResult<WriteupDetailDto> =
        executeJsonRequest {
            httpClient.get(PublicApi.Writeups.BySlug(slug = slug))
        }

    override suspend fun getDocuments(options: QueryOptions): ApiResult<PagedResult<DocumentDto>> =
        executeJsonRequest {
            httpClient.get(
                PublicApi.Documents(
                    page = options.page,
                    pageSize = options.pageSize,
                ),
            )
        }

    override fun buildDocumentDownloadUrl(documentId: String): String =
        "$normalizedBaseUrl/api/v1/public/documents/${documentId.trim()}/download"

    override fun buildReaderHtmlUrl(slug: String): String =
        "$normalizedBaseUrl/read/${slug.trim()}"

    override suspend fun recordEvent(request: TrackEventRequest): ApiResult<Unit> =
        executeCall(
            performRequest = {
                httpClient.post(PublicApi.Events()) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            },
            decodeSuccess = { ApiResult.Success(Unit) },
        )

    private suspend inline fun <reified T> executeJsonRequest(
        crossinline performRequest: suspend () -> HttpResponse,
    ): ApiResult<T> = executeCall(
        performRequest = { performRequest() },
        decodeSuccess = { response -> ApiResult.Success(response.body<T>()) },
    )

    private suspend inline fun <T> executeCall(
        performRequest: () -> HttpResponse,
        decodeSuccess: (HttpResponse) -> ApiResult<T>,
    ): ApiResult<T> =
        try {
            val response = performRequest()
            if (response.status.isSuccess()) {
                decodeSuccess(response)
            } else {
                parseFailureResponse(response)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ex: Throwable) {
            ApiResult.Failure(
                ApiError.InternalError(
                    message = ex.message ?: NETWORK_ERROR_MESSAGE,
                ),
            )
        }

    private suspend fun parseFailureResponse(response: HttpResponse): ApiResult.Failure {
        val rawBody = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching {
            PortfolioJson.decodeFromString(ApiError.serializer(), rawBody)
        }.getOrNull()

        val apiError = parsed ?: fallbackApiError(
            statusCode = response.status.value,
            statusDescription = response.status.description,
            requestPath = response.call.request.url.encodedPath,
        )
        return ApiResult.Failure(apiError)
    }

    private fun fallbackApiError(
        statusCode: Int,
        statusDescription: String,
        requestPath: String,
    ): ApiError = when (statusCode) {
        ApiError.HTTP_BAD_REQUEST -> ApiError.ValidationError(message = "Bad request ($statusDescription).")
        ApiError.HTTP_UNAUTHORIZED -> ApiError.Unauthorized(message = "Unauthorized ($statusDescription).")
        ApiError.HTTP_FORBIDDEN -> ApiError.Forbidden(message = "Forbidden ($statusDescription).")
        ApiError.HTTP_NOT_FOUND -> ApiError.NotFound(
            message = "Resource at '$requestPath' was not found.",
            resource = "endpoint",
            identifier = requestPath,
        )
        ApiError.HTTP_CONFLICT -> ApiError.Conflict(message = "Resource conflict ($statusDescription).")
        else -> ApiError.InternalError(message = "HTTP $statusCode: $statusDescription")
    }

    companion object {
        private const val NETWORK_ERROR_MESSAGE: String = "Unable to reach the portfolio service."
    }
}
