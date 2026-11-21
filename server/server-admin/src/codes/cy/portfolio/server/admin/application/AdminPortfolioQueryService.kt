package codes.cy.portfolio.server.admin.application

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.server.admin.domain.AdminValidation
import codes.cy.portfolio.server.db.errors.NotFoundException
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.repositories.DocumentQueryRepository
import codes.cy.portfolio.server.db.repositories.PortfolioQueryRepository
import codes.cy.portfolio.server.db.repositories.ProjectQueryRepository
import codes.cy.portfolio.server.db.repositories.WriteupQueryRepository

/**
 * Pure CQS read-only query coordinator for admin CMS lists and editors.
 */
class AdminPortfolioQueryService(
    private val portfolioRepo: PortfolioQueryRepository,
    private val projectRepo: ProjectQueryRepository,
    private val writeupRepo: WriteupQueryRepository,
    private val documentRepo: DocumentQueryRepository,
) {
    suspend fun getProfile(): ProfileDto =
        portfolioRepo.findProfile()
            ?: throw NotFoundException(resource = "profile", identifier = "singleton")

    suspend fun getSkills(): List<SkillDto> =
        portfolioRepo.findAllSkills()

    suspend fun getExperiences(): List<ExperienceDto> =
        portfolioRepo.findAllExperiences()

    suspend fun getEducation(): List<EducationDto> =
        portfolioRepo.findAllEducation()

    suspend fun getCertifications(): List<CertificationDto> =
        portfolioRepo.findAllCertifications()

    suspend fun getProjects(options: QueryOptions): PagedResult<ProjectSummaryDto> {
        ensureValidOffset(options)
        return projectRepo.getProjects(options = options, featuredOnly = null, skillName = null)
    }

    suspend fun getProjectById(rawId: String): ProjectDetailDto {
        val projectUuid = AdminValidation.parseUuidOrThrow(rawId, "id")
        return projectRepo.findProjectById(projectUuid)
            ?: throw NotFoundException(resource = "project", identifier = rawId)
    }

    suspend fun getWriteups(options: QueryOptions): PagedResult<WriteupSummaryDto> {
        ensureValidOffset(options)
        return writeupRepo.getWriteups(
            options = options,
            publishedOnly = false,
            tag = null,
            searchQuery = null,
        )
    }

    suspend fun getWriteupById(rawId: String): WriteupDetailDto {
        val writeupUuid = AdminValidation.parseUuidOrThrow(rawId, "id")
        return writeupRepo.findWriteupById(writeupUuid)
            ?: throw NotFoundException(resource = "writeup", identifier = rawId)
    }

    suspend fun getDocuments(options: QueryOptions): PagedResult<DocumentDto> {
        ensureValidOffset(options)
        return documentRepo.getDocuments(options = options, publicOnly = false)
    }

    private fun ensureValidOffset(options: QueryOptions) {
        if (options.calculateOffsetOrNull() == null) {
            throw ValidationException(
                message = "Pagination offset out of bounds for page=${options.page}, pageSize=${options.pageSize}.",
                fieldErrors = listOf(FieldViolation("page", "Pagination bounds overflow 32-bit SQL offset limits.")),
            )
        }
    }
}
