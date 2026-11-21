package codes.cy.portfolio.server.admin.application

import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.UpdateProfileRequest
import codes.cy.portfolio.core.model.UpsertCertificationRequest
import codes.cy.portfolio.core.model.UpsertEducationRequest
import codes.cy.portfolio.core.model.UpsertExperienceRequest
import codes.cy.portfolio.core.model.UpsertProjectRequest
import codes.cy.portfolio.core.model.UpsertSkillRequest
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.server.admin.domain.AdminValidation
import codes.cy.portfolio.server.db.errors.NotFoundException
import codes.cy.portfolio.server.db.repositories.PortfolioCommandRepository
import codes.cy.portfolio.server.db.repositories.ProjectCommandRepository
import codes.cy.portfolio.server.db.repositories.WriteupCommandRepository

/**
 * Side-effecting CMS command coordinator validating inputs before persistence.
 */
class AdminPortfolioCommandService(
    private val portfolioRepo: PortfolioCommandRepository,
    private val projectRepo: ProjectCommandRepository,
    private val writeupRepo: WriteupCommandRepository,
) {
    suspend fun updateProfile(request: UpdateProfileRequest): ProfileDto {
        AdminValidation.validateProfileRequest(request)
        return portfolioRepo.upsertProfile(request)
    }

    suspend fun createSkill(request: UpsertSkillRequest): SkillDto {
        AdminValidation.validateSkillRequest(request)
        return portfolioRepo.createSkill(request)
    }

    suspend fun updateSkill(rawId: String, request: UpsertSkillRequest): SkillDto {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        AdminValidation.validateSkillRequest(request)
        return portfolioRepo.updateSkill(id, request)
            ?: throw NotFoundException(resource = "skill", identifier = rawId)
    }

    suspend fun deleteSkill(rawId: String) {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        if (!portfolioRepo.deleteSkill(id)) {
            throw NotFoundException(resource = "skill", identifier = rawId)
        }
    }

    suspend fun createExperience(request: UpsertExperienceRequest): ExperienceDto {
        AdminValidation.validateExperienceRequest(request)
        return portfolioRepo.createExperience(request)
    }

    suspend fun updateExperience(rawId: String, request: UpsertExperienceRequest): ExperienceDto {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        AdminValidation.validateExperienceRequest(request)
        return portfolioRepo.updateExperience(id, request)
            ?: throw NotFoundException(resource = "experience", identifier = rawId)
    }

    suspend fun deleteExperience(rawId: String) {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        if (!portfolioRepo.deleteExperience(id)) {
            throw NotFoundException(resource = "experience", identifier = rawId)
        }
    }

    suspend fun createEducation(request: UpsertEducationRequest): EducationDto {
        AdminValidation.validateEducationRequest(request)
        return portfolioRepo.createEducation(request)
    }

    suspend fun updateEducation(rawId: String, request: UpsertEducationRequest): EducationDto {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        AdminValidation.validateEducationRequest(request)
        return portfolioRepo.updateEducation(id, request)
            ?: throw NotFoundException(resource = "education", identifier = rawId)
    }

    suspend fun deleteEducation(rawId: String) {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        if (!portfolioRepo.deleteEducation(id)) {
            throw NotFoundException(resource = "education", identifier = rawId)
        }
    }

    suspend fun createCertification(request: UpsertCertificationRequest): CertificationDto {
        AdminValidation.validateCertificationRequest(request)
        return portfolioRepo.createCertification(request)
    }

    suspend fun updateCertification(rawId: String, request: UpsertCertificationRequest): CertificationDto {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        AdminValidation.validateCertificationRequest(request)
        return portfolioRepo.updateCertification(id, request)
            ?: throw NotFoundException(resource = "certification", identifier = rawId)
    }

    suspend fun deleteCertification(rawId: String) {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        if (!portfolioRepo.deleteCertification(id)) {
            throw NotFoundException(resource = "certification", identifier = rawId)
        }
    }

    suspend fun createProject(request: UpsertProjectRequest): ProjectDetailDto {
        AdminValidation.validateProjectRequest(request)
        return projectRepo.createProject(request)
    }

    suspend fun updateProject(rawId: String, request: UpsertProjectRequest): ProjectDetailDto {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        AdminValidation.validateProjectRequest(request)
        return projectRepo.updateProject(id, request)
            ?: throw NotFoundException(resource = "project", identifier = rawId)
    }

    suspend fun deleteProject(rawId: String) {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        if (!projectRepo.deleteProject(id)) {
            throw NotFoundException(resource = "project", identifier = rawId)
        }
    }

    suspend fun createWriteup(request: UpsertWriteupRequest): WriteupDetailDto {
        AdminValidation.validateWriteupRequest(request)
        return writeupRepo.createWriteup(request)
    }

    suspend fun updateWriteup(rawId: String, request: UpsertWriteupRequest): WriteupDetailDto {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        AdminValidation.validateWriteupRequest(request)
        return writeupRepo.updateWriteup(id, request)
            ?: throw NotFoundException(resource = "writeup", identifier = rawId)
    }

    suspend fun deleteWriteup(rawId: String) {
        val id = AdminValidation.parseUuidOrThrow(rawId, "id")
        if (!writeupRepo.deleteWriteup(id)) {
            throw NotFoundException(resource = "writeup", identifier = rawId)
        }
    }
}
