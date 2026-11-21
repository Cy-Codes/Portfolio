package codes.cy.portfolio.server.admin

import codes.cy.portfolio.core.serialization.PortfolioJson
import codes.cy.portfolio.server.admin.api.configureAdminStatusPages
import codes.cy.portfolio.server.admin.api.registerAdminRoutes
import codes.cy.portfolio.server.admin.application.AdminAppModules
import codes.cy.portfolio.server.admin.domain.AdminServerConfig
import codes.cy.portfolio.server.admin.infrastructure.AdminServerDependencies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

fun main() {
    val config = AdminServerConfig.fromEnv()
    val dependencies = AdminServerDependencies.create(config = config)
    embeddedServer(Netty, port = config.port, host = config.host) {
        monitor.subscribe(ApplicationStopped) {
            dependencies.close()
        }
        adminModule(dependencies.modules)
    }.start(wait = true)
}

fun Application.adminModule(modules: AdminAppModules) {
    val logger = LoggerFactory.getLogger("codes.cy.portfolio.server.admin")

    install(ContentNegotiation) {
        json(PortfolioJson)
    }
    install(Resources)
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("X-Frame-Options", "DENY")
        header("Cache-Control", "no-store")
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("Cf-Access-Jwt-Assertion")
        modules.config.corsAllowedOrigins.forEach { origin ->
            allowHost(origin)
        }
        if (modules.config.isDevelopment && modules.config.corsAllowedOrigins.isEmpty()) {
            anyHost()
        }
    }
    install(CallLogging)
    configureAdminStatusPages(isDevelopment = modules.config.isDevelopment, logger = logger)

    routing {
        registerAdminRoutes(
            authenticator = modules.authenticator,
            readinessChecker = modules.readinessChecker,
            queryService = modules.queryService,
            commandService = modules.commandService,
            documentService = modules.documentService,
            statsService = modules.statsService,
        )
    }
}
