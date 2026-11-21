package codes.cy.portfolio.server.admin.api

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond

suspend inline fun <reified T : Any> ApplicationCall.respondCreated(
    locationPath: String,
    body: T,
) {
    response.header(HttpHeaders.Location, locationPath)
    respond(HttpStatusCode.Created, body)
}
