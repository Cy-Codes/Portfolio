package codes.cy.portfolio.admin.data

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.AdminSessionDto
import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.PresignUploadRequest
import codes.cy.portfolio.core.model.PresignUploadResponse
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.StatsOverviewDto
import codes.cy.portfolio.core.model.StatsTimeRange
import codes.cy.portfolio.core.model.UpdateProfileRequest
import codes.cy.portfolio.core.model.UpsertCertificationRequest
import codes.cy.portfolio.core.model.UpsertEducationRequest
import codes.cy.portfolio.core.model.UpsertExperienceRequest
import codes.cy.portfolio.core.model.UpsertProjectRequest
import codes.cy.portfolio.core.model.UpsertSkillRequest
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.routes.AdminApi
import codes.cy.portfolio.core.serialization.PortfolioJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pure read-only queries against `/api/v1/admin` routes per CQS.
 */
interface AdminQueryApi {
    suspend fun getSession(): AdminApiResult<AdminSessionDto>

    suspend fun getStatsOverview(range: StatsTimeRange = StatsTimeRange.THIRTY_DAYS): AdminApiResult<StatsOverviewDto>

    suspend fun getProfile(): AdminApiResult<ProfileDto>

    suspend fun getSkills(): AdminApiResult<List<SkillDto>>

    suspend fun getExperiences(): AdminApiResult<List<ExperienceDto>>

    suspend fun getEducation(): AdminApiResult<List<EducationDto>>

    suspend fun getCertifications(): AdminApiResult<List<CertificationDto>>

    suspend fun getProjects(options: QueryOptions = QueryOptions.DEFAULT): AdminApiResult<PagedResult<ProjectSummaryDto>>

    suspend fun getProjectById(id: String): AdminApiResult<ProjectDetailDto>

    suspend fun getWriteups(options: QueryOptions = QueryOptions.DEFAULT): AdminApiResult<PagedResult<WriteupSummaryDto>>

    suspend fun getWriteupById(id: String): AdminApiResult<WriteupDetailDto>

    suspend fun getDocuments(options: QueryOptions = QueryOptions.DEFAULT): AdminApiResult<PagedResult<DocumentDto>>
}

/**
 * Side-effecting admin mutations and direct-to-R2/MinIO binary PUT upload per CQS.
 */
interface AdminCommandApi {
    suspend fun updateProfile(request: UpdateProfileRequest): AdminApiResult<ProfileDto>

    suspend fun createSkill(request: UpsertSkillRequest): AdminApiResult<SkillDto>

    suspend fun updateSkill(id: String, request: UpsertSkillRequest): AdminApiResult<SkillDto>

    suspend fun deleteSkill(id: String): AdminApiResult<Unit>

    suspend fun createExperience(request: UpsertExperienceRequest): AdminApiResult<ExperienceDto>

    suspend fun updateExperience(id: String, request: UpsertExperienceRequest): AdminApiResult<ExperienceDto>

    suspend fun deleteExperience(id: String): AdminApiResult<Unit>

    suspend fun createEducation(request: UpsertEducationRequest): AdminApiResult<EducationDto>

    suspend fun updateEducation(id: String, request: UpsertEducationRequest): AdminApiResult<EducationDto>

    suspend fun deleteEducation(id: String): AdminApiResult<Unit>

    suspend fun createCertification(request: UpsertCertificationRequest): AdminApiResult<CertificationDto>

    suspend fun updateCertification(id: String, request: UpsertCertificationRequest): AdminApiResult<CertificationDto>

    suspend fun deleteCertification(id: String): AdminApiResult<Unit>

    suspend fun createProject(request: UpsertProjectRequest): AdminApiResult<ProjectDetailDto>

    suspend fun updateProject(id: String, request: UpsertProjectRequest): AdminApiResult<ProjectDetailDto>

    suspend fun deleteProject(id: String): AdminApiResult<Unit>

    suspend fun createWriteup(request: UpsertWriteupRequest): AdminApiResult<WriteupDetailDto>

    suspend fun updateWriteup(id: String, request: UpsertWriteupRequest): AdminApiResult<WriteupDetailDto>

    suspend fun deleteWriteup(id: String): AdminApiResult<Unit>

    suspend fun presignDocumentUpload(request: PresignUploadRequest): AdminApiResult<PresignUploadResponse>

    suspend fun uploadBinaryToPresignedUrl(
        presignedPutUrl: String,
        requiredHeaders: Map<String, String>,
        mimeType: String,
        bytes: ByteArray,
    ): AdminApiResult<Unit>

    suspend fun confirmDocumentUpload(request: ConfirmUploadRequest): AdminApiResult<DocumentDto>

    suspend fun deleteDocument(id: String): AdminApiResult<Unit>
}

class KtorAdminApiClient(
    private val apiHttpClient: HttpClient,
    private val binaryUploadClient: HttpClient,
) : AdminQueryApi, AdminCommandApi {

    override suspend fun getSession(): AdminApiResult<AdminSessionDto> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.AuthSession())
        }

    override suspend fun getStatsOverview(range: StatsTimeRange): AdminApiResult<StatsOverviewDto> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.StatsOverview(range = range.wireValue))
        }

    override suspend fun getProfile(): AdminApiResult<ProfileDto> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Profile())
        }

    override suspend fun updateProfile(request: UpdateProfileRequest): AdminApiResult<ProfileDto> =
        executeJsonRequest {
            apiHttpClient.put(AdminApi.Profile()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun getSkills(): AdminApiResult<List<SkillDto>> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Skills())
        }

    override suspend fun createSkill(request: UpsertSkillRequest): AdminApiResult<SkillDto> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Skills()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun updateSkill(id: String, request: UpsertSkillRequest): AdminApiResult<SkillDto> =
        executeJsonRequest {
            apiHttpClient.put(AdminApi.Skills.ById(id = id)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun deleteSkill(id: String): AdminApiResult<Unit> =
        executeUnitRequest {
            apiHttpClient.delete(AdminApi.Skills.ById(id = id))
        }

    override suspend fun getExperiences(): AdminApiResult<List<ExperienceDto>> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Experiences())
        }

    override suspend fun createExperience(request: UpsertExperienceRequest): AdminApiResult<ExperienceDto> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Experiences()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun updateExperience(id: String, request: UpsertExperienceRequest): AdminApiResult<ExperienceDto> =
        executeJsonRequest {
            apiHttpClient.put(AdminApi.Experiences.ById(id = id)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun deleteExperience(id: String): AdminApiResult<Unit> =
        executeUnitRequest {
            apiHttpClient.delete(AdminApi.Experiences.ById(id = id))
        }

    override suspend fun getEducation(): AdminApiResult<List<EducationDto>> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Education())
        }

    override suspend fun createEducation(request: UpsertEducationRequest): AdminApiResult<EducationDto> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Education()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun updateEducation(id: String, request: UpsertEducationRequest): AdminApiResult<EducationDto> =
        executeJsonRequest {
            apiHttpClient.put(AdminApi.Education.ById(id = id)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun deleteEducation(id: String): AdminApiResult<Unit> =
        executeUnitRequest {
            apiHttpClient.delete(AdminApi.Education.ById(id = id))
        }

    override suspend fun getCertifications(): AdminApiResult<List<CertificationDto>> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Certifications())
        }

    override suspend fun createCertification(request: UpsertCertificationRequest): AdminApiResult<CertificationDto> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Certifications()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun updateCertification(
        id: String,
        request: UpsertCertificationRequest,
    ): AdminApiResult<CertificationDto> =
        executeJsonRequest {
            apiHttpClient.put(AdminApi.Certifications.ById(id = id)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun deleteCertification(id: String): AdminApiResult<Unit> =
        executeUnitRequest {
            apiHttpClient.delete(AdminApi.Certifications.ById(id = id))
        }

    override suspend fun getProjects(options: QueryOptions): AdminApiResult<PagedResult<ProjectSummaryDto>> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Projects(page = options.page, pageSize = options.pageSize))
        }

    override suspend fun getProjectById(id: String): AdminApiResult<ProjectDetailDto> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Projects.ById(id = id))
        }

    override suspend fun createProject(request: UpsertProjectRequest): AdminApiResult<ProjectDetailDto> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Projects()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun updateProject(id: String, request: UpsertProjectRequest): AdminApiResult<ProjectDetailDto> =
        executeJsonRequest {
            apiHttpClient.put(AdminApi.Projects.ById(id = id)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun deleteProject(id: String): AdminApiResult<Unit> =
        executeUnitRequest {
            apiHttpClient.delete(AdminApi.Projects.ById(id = id))
        }

    override suspend fun getWriteups(options: QueryOptions): AdminApiResult<PagedResult<WriteupSummaryDto>> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Writeups(page = options.page, pageSize = options.pageSize))
        }

    override suspend fun getWriteupById(id: String): AdminApiResult<WriteupDetailDto> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Writeups.ById(id = id))
        }

    override suspend fun createWriteup(request: UpsertWriteupRequest): AdminApiResult<WriteupDetailDto> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Writeups()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun updateWriteup(id: String, request: UpsertWriteupRequest): AdminApiResult<WriteupDetailDto> =
        executeJsonRequest {
            apiHttpClient.put(AdminApi.Writeups.ById(id = id)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun deleteWriteup(id: String): AdminApiResult<Unit> =
        executeUnitRequest {
            apiHttpClient.delete(AdminApi.Writeups.ById(id = id))
        }

    override suspend fun getDocuments(options: QueryOptions): AdminApiResult<PagedResult<DocumentDto>> =
        executeJsonRequest {
            apiHttpClient.get(AdminApi.Documents(page = options.page, pageSize = options.pageSize))
        }

    override suspend fun presignDocumentUpload(request: PresignUploadRequest): AdminApiResult<PresignUploadResponse> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Documents.Presign()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun uploadBinaryToPresignedUrl(
        presignedPutUrl: String,
        requiredHeaders: Map<String, String>,
        mimeType: String,
        bytes: ByteArray,
    ): AdminApiResult<Unit> =
        executeUnitRequest {
            val resolvedType = runCatching { ContentType.parse(mimeType) }
                .getOrDefault(ContentType.Application.OctetStream)
            binaryUploadClient.put(presignedPutUrl) {
                requiredHeaders.entries
                    .filter { (key, _) -> key.lowercase() !in MANAGED_UPLOAD_HEADERS }
                    .forEach { (key, value) -> header(key, value) }
                setBody(ByteArrayContent(bytes = bytes, contentType = resolvedType))
            }
        }

    override suspend fun confirmDocumentUpload(request: ConfirmUploadRequest): AdminApiResult<DocumentDto> =
        executeJsonRequest {
            apiHttpClient.post(AdminApi.Documents.Confirm()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun deleteDocument(id: String): AdminApiResult<Unit> =
        executeUnitRequest {
            apiHttpClient.delete(AdminApi.Documents.ById(id = id))
        }

    private suspend inline fun <reified T> executeJsonRequest(
        crossinline performRequest: suspend () -> HttpResponse,
    ): AdminApiResult<T> =
        executeCall(
            performRequest = { performRequest() },
            decodeSuccess = { response -> AdminApiResult.Success(response.body<T>()) },
        )

    private suspend inline fun executeUnitRequest(
        crossinline performRequest: suspend () -> HttpResponse,
    ): AdminApiResult<Unit> =
        executeCall(
            performRequest = { performRequest() },
            decodeSuccess = { AdminApiResult.Success(Unit) },
        )

    private suspend inline fun <T> executeCall(
        performRequest: () -> HttpResponse,
        decodeSuccess: (HttpResponse) -> AdminApiResult<T>,
    ): AdminApiResult<T> =
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
            AdminApiResult.Failure(
                ApiError.InternalError(
                    message = ex.message ?: NETWORK_ERROR_MESSAGE,
                ),
            )
        }

    private suspend fun parseFailureResponse(response: HttpResponse): AdminApiResult.Failure {
        val rawBody = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching {
            PortfolioJson.decodeFromString(ApiError.serializer(), rawBody)
        }.getOrNull()

        val apiError = parsed ?: fallbackApiError(
            statusCode = response.status.value,
            statusDescription = response.status.description,
            requestPath = response.call.request.url.encodedPath,
        )
        return AdminApiResult.Failure(apiError)
    }

    private fun fallbackApiError(
        statusCode: Int,
        statusDescription: String,
        requestPath: String,
    ): ApiError =
        when (statusCode) {
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
        private const val NETWORK_ERROR_MESSAGE: String = "Unable to reach the admin or storage endpoint."
        private val MANAGED_UPLOAD_HEADERS: Set<String> = setOf("host", "content-type", "content-length")
    }
}
