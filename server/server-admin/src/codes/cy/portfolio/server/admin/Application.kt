package codes.cy.portfolio.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val DEFAULT_ADMIN_HOST = "127.0.0.1"
private const val DEFAULT_ADMIN_PORT = 8081
private const val LIVENESS_PATH = "/health"
private const val LIVENESS_BODY = "OK"

fun main() {
    val port = System.getenv("ADMIN_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_ADMIN_PORT
    embeddedServer(Netty, port = port, host = DEFAULT_ADMIN_HOST, module = Application::adminModule)
        .start(wait = true)
}

fun Application.adminModule() {
    routing {
        get(LIVENESS_PATH) {
            call.respondText(LIVENESS_BODY, status = HttpStatusCode.OK)
        }
    }
}
