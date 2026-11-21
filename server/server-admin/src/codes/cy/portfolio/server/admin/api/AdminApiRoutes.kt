package codes.cy.portfolio.server.admin.api

import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.HealthStatusDto
import codes.cy.portfolio.core.model.PresignUploadRequest
import codes.cy.portfolio.core.model.UpdateProfileRequest
import codes.cy.portfolio.core.model.UpsertCertificationRequest
import codes.cy.portfolio.core.model.UpsertEducationRequest
import codes.cy.portfolio.core.model.UpsertExperienceRequest
import codes.cy.portfolio.core.model.UpsertProjectRequest
import codes.cy.portfolio.core.model.UpsertSkillRequest
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.routes.AdminApi
import codes.cy.portfolio.core.routes.HealthRoute
import codes.cy.portfolio.server.admin.application.AdminDocumentCommandService
import codes.cy.portfolio.server.admin.application.AdminPortfolioCommandService
import codes.cy.portfolio.server.admin.application.AdminPortfolioQueryService
import codes.cy.portfolio.server.admin.application.AdminStatsQueryService
import codes.cy.portfolio.server.admin.application.ReadinessChecker
import codes.cy.portfolio.server.admin.infrastructure.AdminTokenAuthenticator
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

private const val SERVICE_NAME: String = "server-admin"
private const val STATUS_UP: String = "UP"
private const val STATUS_READY: String = "READY"
private const val STATUS_NOT_READY: String = "NOT_READY"

fun Route.registerAdminRoutes(
    authenticator: AdminTokenAuthenticator,
    readinessChecker: ReadinessChecker,
    queryService: AdminPortfolioQueryService,
    commandService: AdminPortfolioCommandService,
    documentService: AdminDocumentCommandService,
    statsService: AdminStatsQueryService,
) {
    get<HealthRoute> {
        call.respond(
            HttpStatusCode.OK,
            HealthStatusDto(status = STATUS_UP, service = SERVICE_NAME),
        )
    }

    get<HealthRoute.Ready> {
        val status = readinessChecker.check()
        val httpStatus = if (status.ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(
            httpStatus,
            HealthStatusDto(
                status = if (status.ready) STATUS_READY else STATUS_NOT_READY,
                service = SERVICE_NAME,
                databaseReady = status.ready,
                appliedMigrationCount = status.appliedMigrationCount,
            ),
        )
    }

    authenticatedAdminScope(authenticator) {
        get<AdminApi.AuthSession> {
            val principal = call.attributes[AdminPrincipalKey]
            call.respond(HttpStatusCode.OK, principal.toSessionDto())
        }

        get<AdminApi.Profile> {
            call.respond(HttpStatusCode.OK, queryService.getProfile())
        }

        put<AdminApi.Profile> {
            val request = call.receive<UpdateProfileRequest>()
            call.respond(HttpStatusCode.OK, commandService.updateProfile(request))
        }

        get<AdminApi.Skills> {
            call.respond(HttpStatusCode.OK, queryService.getSkills())
        }

        post<AdminApi.Skills> {
            val created = commandService.createSkill(call.receive<UpsertSkillRequest>())
            call.respondCreated("/api/v1/admin/skills/${created.id}", created)
        }

        put<AdminApi.Skills.ById> { route ->
            val updated = commandService.updateSkill(route.id, call.receive<UpsertSkillRequest>())
            call.respond(HttpStatusCode.OK, updated)
        }

        delete<AdminApi.Skills.ById> { route ->
            commandService.deleteSkill(route.id)
            call.respond(HttpStatusCode.NoContent)
        }

        get<AdminApi.Experiences> {
            call.respond(HttpStatusCode.OK, queryService.getExperiences())
        }

        post<AdminApi.Experiences> {
            val created = commandService.createExperience(call.receive<UpsertExperienceRequest>())
            call.respondCreated("/api/v1/admin/experiences/${created.id}", created)
        }

        put<AdminApi.Experiences.ById> { route ->
            val updated = commandService.updateExperience(route.id, call.receive<UpsertExperienceRequest>())
            call.respond(HttpStatusCode.OK, updated)
        }

        delete<AdminApi.Experiences.ById> { route ->
            commandService.deleteExperience(route.id)
            call.respond(HttpStatusCode.NoContent)
        }

        get<AdminApi.Education> {
            call.respond(HttpStatusCode.OK, queryService.getEducation())
        }

        post<AdminApi.Education> {
            val created = commandService.createEducation(call.receive<UpsertEducationRequest>())
            call.respondCreated("/api/v1/admin/education/${created.id}", created)
        }

        put<AdminApi.Education.ById> { route ->
            val updated = commandService.updateEducation(route.id, call.receive<UpsertEducationRequest>())
            call.respond(HttpStatusCode.OK, updated)
        }

        delete<AdminApi.Education.ById> { route ->
            commandService.deleteEducation(route.id)
            call.respond(HttpStatusCode.NoContent)
        }

        get<AdminApi.Certifications> {
            call.respond(HttpStatusCode.OK, queryService.getCertifications())
        }

        post<AdminApi.Certifications> {
            val created = commandService.createCertification(call.receive<UpsertCertificationRequest>())
            call.respondCreated("/api/v1/admin/certifications/${created.id}", created)
        }

        put<AdminApi.Certifications.ById> { route ->
            val updated = commandService.updateCertification(route.id, call.receive<UpsertCertificationRequest>())
            call.respond(HttpStatusCode.OK, updated)
        }

        delete<AdminApi.Certifications.ById> { route ->
            commandService.deleteCertification(route.id)
            call.respond(HttpStatusCode.NoContent)
        }

        get<AdminApi.Projects> { route ->
            call.respond(HttpStatusCode.OK, queryService.getProjects(route.toValidatedQueryOptions()))
        }

        get<AdminApi.Projects.ById> { route ->
            call.respond(HttpStatusCode.OK, queryService.getProjectById(route.id))
        }

        post<AdminApi.Projects> {
            val created = commandService.createProject(call.receive<UpsertProjectRequest>())
            call.respondCreated("/api/v1/admin/projects/${created.id}", created)
        }

        put<AdminApi.Projects.ById> { route ->
            val updated = commandService.updateProject(route.id, call.receive<UpsertProjectRequest>())
            call.respond(HttpStatusCode.OK, updated)
        }

        delete<AdminApi.Projects.ById> { route ->
            commandService.deleteProject(route.id)
            call.respond(HttpStatusCode.NoContent)
        }

        get<AdminApi.Writeups> { route ->
            call.respond(HttpStatusCode.OK, queryService.getWriteups(route.toValidatedQueryOptions()))
        }

        get<AdminApi.Writeups.ById> { route ->
            call.respond(HttpStatusCode.OK, queryService.getWriteupById(route.id))
        }

        post<AdminApi.Writeups> {
            val created = commandService.createWriteup(call.receive<UpsertWriteupRequest>())
            call.respondCreated("/api/v1/admin/writeups/${created.id}", created)
        }

        put<AdminApi.Writeups.ById> { route ->
            val updated = commandService.updateWriteup(route.id, call.receive<UpsertWriteupRequest>())
            call.respond(HttpStatusCode.OK, updated)
        }

        delete<AdminApi.Writeups.ById> { route ->
            commandService.deleteWriteup(route.id)
            call.respond(HttpStatusCode.NoContent)
        }

        get<AdminApi.Documents> { route ->
            call.respond(HttpStatusCode.OK, queryService.getDocuments(route.toValidatedQueryOptions()))
        }

        post<AdminApi.Documents.Presign> {
            val presigned = documentService.presignUpload(call.receive<PresignUploadRequest>())
            call.respond(HttpStatusCode.OK, presigned)
        }

        post<AdminApi.Documents.Confirm> {
            val created = documentService.confirmUpload(call.receive<ConfirmUploadRequest>())
            call.respondCreated("/api/v1/admin/documents/${created.id}", created)
        }

        delete<AdminApi.Documents.ById> { route ->
            documentService.deleteDocument(route.id)
            call.respond(HttpStatusCode.NoContent)
        }

        get<AdminApi.StatsOverview> { route ->
            call.respond(HttpStatusCode.OK, statsService.getOverview(route.range))
        }
    }
}
