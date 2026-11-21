package codes.cy.portfolio.server.publicapi.api

import codes.cy.portfolio.core.model.HealthStatusDto
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.routes.HealthRoute
import codes.cy.portfolio.core.routes.PublicApi
import codes.cy.portfolio.server.publicapi.application.PublicAnalyticsCommandService
import codes.cy.portfolio.server.publicapi.application.PublicPortfolioQueryService
import codes.cy.portfolio.server.publicapi.application.ReadinessChecker
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.userAgent
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route

private const val SERVICE_NAME: String = "server-public"
private const val STATUS_UP: String = "UP"
private const val STATUS_READY: String = "READY"
private const val STATUS_NOT_READY: String = "NOT_READY"
private const val DOWNLOAD_CACHE_CONTROL: String = "private, max-age=300"
private const val HEADER_CONTENT_TYPE_OPTIONS_NOSNIFF: String = "nosniff"
private const val HEADER_CF_CONNECTING_IP: String = "CF-Connecting-IP"
private const val HEADER_X_FORWARDED_FOR: String = "X-Forwarded-For"

fun Route.registerPublicApiRoutes(
    readinessChecker: ReadinessChecker,
    queryService: PublicPortfolioQueryService,
    analyticsService: PublicAnalyticsCommandService,
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

    get<PublicApi.Portfolio> {
        call.respond(HttpStatusCode.OK, queryService.getPortfolioOverview())
    }

    get<PublicApi.Projects> { route ->
        val options = route.toValidatedQueryOptions()
        call.respond(
            HttpStatusCode.OK,
            queryService.getProjects(
                options = options,
                featuredOnly = route.featured,
                skillName = route.skill,
            ),
        )
    }

    get<PublicApi.Projects.BySlug> { route ->
        call.respond(HttpStatusCode.OK, queryService.getProjectBySlug(route.slug))
    }

    get<PublicApi.Writeups> { route ->
        val options = route.toValidatedQueryOptions()
        call.respond(
            HttpStatusCode.OK,
            queryService.getWriteups(
                options = options,
                tag = route.tag,
                searchQuery = route.q,
            ),
        )
    }

    get<PublicApi.Writeups.BySlug> { route ->
        call.respond(HttpStatusCode.OK, queryService.getWriteupBySlug(route.slug))
    }

    get<PublicApi.Documents.Download> { route ->
        val resolved = queryService.resolvePublicDocumentDownload(route.id)
        val safeFilename = resolved.document.originalFilename.replace("\"", "_")
        call.response.header("X-Content-Type-Options", HEADER_CONTENT_TYPE_OPTIONS_NOSNIFF)
        call.response.header(HttpHeaders.CacheControl, DOWNLOAD_CACHE_CONTROL)
        call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"$safeFilename\"")
        call.respondRedirect(url = resolved.presignedUrl, permanent = false)
    }

    post<PublicApi.Events> {
        val request = call.receive<TrackEventRequest>()
        val clientIp = call.request.header(HEADER_CF_CONNECTING_IP)
            ?: call.request.header(HEADER_X_FORWARDED_FOR)?.substringBefore(',')?.trim()
            ?: call.request.local.remoteAddress
        val userAgent = call.request.userAgent().orEmpty()
        analyticsService.recordEvent(
            request = request,
            clientIp = clientIp,
            userAgent = userAgent,
        )
        call.respond(HttpStatusCode.Accepted)
    }
}
