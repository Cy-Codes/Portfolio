package codes.cy.portfolio.server.publicapi

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val DEFAULT_PUBLIC_HOST = "0.0.0.0"
private const val DEFAULT_PUBLIC_PORT = 8080
private const val LIVENESS_PATH = "/health"
private const val LIVENESS_BODY = "OK"

fun main() {
    val port = System.getenv("PUBLIC_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PUBLIC_PORT
    embeddedServer(Netty, port = port, host = DEFAULT_PUBLIC_HOST, module = Application::publicModule)
        .start(wait = true)
}

fun Application.publicModule() {
    routing {
        get(LIVENESS_PATH) {
            call.respondText(LIVENESS_BODY, status = HttpStatusCode.OK)
        }
    }
}
