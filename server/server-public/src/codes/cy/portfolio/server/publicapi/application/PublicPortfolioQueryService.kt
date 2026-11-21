package codes.cy.portfolio.server.publicapi.application

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.validation.isSafeSlug
import codes.cy.portfolio.server.db.errors.NotFoundException
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.repositories.DocumentQueryRepository
import codes.cy.portfolio.server.db.repositories.PortfolioQueryRepository
import codes.cy.portfolio.server.db.repositories.ProjectQueryRepository
import codes.cy.portfolio.server.db.repositories.WriteupQueryRepository
import codes.cy.portfolio.server.db.storage.BlobStorageReader
import codes.cy.portfolio.server.publicapi.domain.SeoMetadataBuilder
import codes.cy.portfolio.server.publicapi.domain.SeoPageMetadata
import kotlin.uuid.Uuid

data class ResolvedDocumentDownload(
    val document: DocumentDto,
    val presignedUrl: String,
)

/**
 * Pure CQS query coordinator for public read APIs, SEO HTML rendering, and read-only downloads.
 * Holds only [BlobStorageReader] so public binaries have no compile-time upload or delete access.
 */
class PublicPortfolioQueryService(
    private val portfolioRepo: PortfolioQueryRepository,
    private val projectRepo: ProjectQueryRepository,
    private val writeupRepo: WriteupQueryRepository,
    private val documentRepo: DocumentQueryRepository,
    private val blobStorageReader: BlobStorageReader,
    private val siteBaseUrl: String,
) {
    suspend fun getPortfolioOverview(): PortfolioOverviewDto =
        portfolioRepo.getPortfolioOverview()

    suspend fun getProjects(
        options: QueryOptions,
        featuredOnly: Boolean?,
        skillName: String?,
    ): PagedResult<ProjectSummaryDto> {
        ensureValidOffset(options)
        return projectRepo.getProjects(
            options = options,
            featuredOnly = featuredOnly,
            skillName = skillName?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun getProjectBySlug(slug: String): ProjectDetailDto {
        val cleanSlug = validateSlug(slug, fieldName = "slug")
        return projectRepo.findProjectBySlug(cleanSlug)
            ?: throw NotFoundException(resource = "project", identifier = cleanSlug)
    }

    suspend fun getWriteups(
        options: QueryOptions,
        tag: String?,
        searchQuery: String?,
    ): PagedResult<WriteupSummaryDto> {
        ensureValidOffset(options)
        return writeupRepo.getWriteups(
            options = options,
            publishedOnly = true,
            tag = tag?.trim()?.takeIf { it.isNotEmpty() },
            searchQuery = searchQuery?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun getWriteupBySlug(slug: String): WriteupDetailDto {
        val cleanSlug = validateSlug(slug, fieldName = "slug")
        return writeupRepo.findWriteupBySlug(cleanSlug, publishedOnly = true)
            ?: throw NotFoundException(resource = "writeup", identifier = cleanSlug)
    }

    /**
     * Pure read-only download resolver performing zero database writes.
     */
    suspend fun resolvePublicDocumentDownload(rawId: String): ResolvedDocumentDownload {
        val documentUuid = runCatching { Uuid.parse(rawId.trim()) }.getOrElse {
            throw ValidationException(
                message = "Invalid document UUID '$rawId'.",
                fieldErrors = listOf(FieldViolation(field = "id", message = "Must be a valid UUID.")),
            )
        }
        val document = documentRepo.findDocumentById(documentUuid, publicOnly = true)
            ?: throw NotFoundException(resource = "document", identifier = rawId)
        val presignedUrl = blobStorageReader.presignGet(
            storageKey = document.storageKey,
            downloadFilename = document.originalFilename,
            contentType = document.mimeType,
        )
        return ResolvedDocumentDownload(document = document, presignedUrl = presignedUrl)
    }

    suspend fun loadSitemapXml(): String {
        val projectSlugs = projectRepo.getProjects(
            options = QueryOptions(page = QueryOptions.DEFAULT_PAGE, pageSize = QueryOptions.MAX_PAGE_SIZE),
            featuredOnly = null,
            skillName = null,
        ).items.map { it.slug }

        val writeupSlugs = writeupRepo.getWriteups(
            options = QueryOptions(page = QueryOptions.DEFAULT_PAGE, pageSize = QueryOptions.MAX_PAGE_SIZE),
            publishedOnly = true,
            tag = null,
            searchQuery = null,
        ).items.map { it.slug }

        return SeoMetadataBuilder.renderSitemapXml(
            baseUrl = siteBaseUrl,
            projectSlugs = projectSlugs,
            writeupSlugs = writeupSlugs,
        )
    }

    fun getRobotsTxt(): String =
        SeoMetadataBuilder.renderRobotsTxt(siteBaseUrl)

    suspend fun resolveSeoMetadata(requestPath: String): SeoPageMetadata {
        val normalizedPath = requestPath.substringBefore('?').trimEnd('/').ifEmpty { "/" }
        val writeupSlug = normalizedPath.extractSlugAfterPrefix("/writeups/")
            ?: normalizedPath.extractSlugAfterPrefix("/read/")
        if (writeupSlug != null && writeupSlug.isSafeSlug()) {
            val writeup = writeupRepo.findWriteupBySlug(writeupSlug, publishedOnly = true)
            if (writeup != null) {
                return SeoMetadataBuilder.forWriteup(
                    baseUrl = siteBaseUrl,
                    canonicalPath = normalizedPath,
                    writeup = writeup,
                )
            }
        }

        val projectSlug = normalizedPath.extractSlugAfterPrefix("/projects/")
        if (projectSlug != null && projectSlug.isSafeSlug()) {
            val project = projectRepo.findProjectBySlug(projectSlug)
            if (project != null) {
                return SeoMetadataBuilder.forProject(
                    baseUrl = siteBaseUrl,
                    project = project,
                )
            }
        }

        val overview = portfolioRepo.getPortfolioOverview()
        return SeoMetadataBuilder.forHome(
            baseUrl = siteBaseUrl,
            canonicalPath = normalizedPath,
            overview = overview,
        )
    }

    private fun String.extractSlugAfterPrefix(prefix: String): String? =
        if (startsWith(prefix)) {
            removePrefix(prefix).takeIf { it.isNotEmpty() && '/' !in it }
        } else {
            null
        }

    private fun ensureValidOffset(options: QueryOptions) {
        if (options.calculateOffsetOrNull() == null) {
            throw ValidationException(
                message = "Pagination offset out of bounds for page=${options.page}, pageSize=${options.pageSize}.",
                fieldErrors = listOf(
                    FieldViolation(field = "page", message = "Pagination bounds overflow 32-bit offset limits."),
                ),
            )
        }
    }

    private fun validateSlug(rawSlug: String, fieldName: String): String {
        val trimmed = rawSlug.trim()
        if (!trimmed.isSafeSlug()) {
            throw ValidationException(
                message = "Invalid slug '$rawSlug'. Expected lowercase alphanumeric hyphenated slug.",
                fieldErrors = listOf(FieldViolation(field = fieldName, message = "Invalid slug format.")),
            )
        }
        return trimmed
    }
}
