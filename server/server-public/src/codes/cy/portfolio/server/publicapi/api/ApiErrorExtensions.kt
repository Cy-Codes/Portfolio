package codes.cy.portfolio.server.publicapi.api

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.error.httpStatusCode
import codes.cy.portfolio.core.serialization.PortfolioJson
import codes.cy.portfolio.server.db.errors.PortfolioException
import codes.cy.portfolio.server.db.errors.toApiError
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import kotlin.uuid.Uuid
import kotlinx.serialization.SerializationException
import org.slf4j.Logger

fun ApiError.toHttpStatusCode(): HttpStatusCode =
    HttpStatusCode.fromValue(httpStatusCode())

suspend fun ApplicationCall.respondApiError(error: ApiError) {
    val status = error.toHttpStatusCode()
    val payload = PortfolioJson.encodeToString(ApiError.serializer(), error)
    respondText(text = payload, contentType = ContentType.Application.Json, status = status)
}

fun Application.configurePublicStatusPages(
    isDevelopment: Boolean,
    logger: Logger,
) {
    install(StatusPages) {
        exception<PortfolioException> { call, cause ->
            call.respondApiError(cause.toApiError(isDevelopment = isDevelopment))
        }
        exception<BadRequestException> { call, cause ->
            call.respondApiError(
                ApiError.ValidationError(
                    message = cause.message ?: "Malformed request payload.",
                    fieldErrors = listOf(FieldViolation(field = "request", message = "Invalid request syntax.")),
                ),
            )
        }
        exception<SerializationException> { call, cause ->
            call.respondApiError(
                ApiError.ValidationError(
                    message = if (isDevelopment) {
                        cause.message ?: "Invalid JSON payload."
                    } else {
                        "Invalid JSON payload."
                    },
                    fieldErrors = listOf(FieldViolation(field = "body", message = "Malformed JSON payload.")),
                ),
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondApiError(cause.toApiError(isDevelopment = isDevelopment))
        }
        exception<Throwable> { call, cause ->
            val traceId = Uuid.random().toString()
            logger.error("Unhandled exception [traceId=$traceId] on ${call.request.uri}", cause)
            call.respondApiError(cause.toApiError(isDevelopment = isDevelopment, traceId = traceId))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondApiError(
                ApiError.NotFound(
                    message = "Route '${call.request.uri}' was not found.",
                    resource = "route",
                    identifier = call.request.uri,
                ),
            )
        }
    }
}
