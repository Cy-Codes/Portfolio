package codes.cy.portfolio.server.publicapi

import codes.cy.portfolio.core.serialization.PortfolioJson
import codes.cy.portfolio.server.publicapi.api.configurePublicStatusPages
import codes.cy.portfolio.server.publicapi.api.registerPublicApiRoutes
import codes.cy.portfolio.server.publicapi.api.registerPublicSeoRoutes
import codes.cy.portfolio.server.publicapi.application.PublicAppModules
import codes.cy.portfolio.server.publicapi.domain.PublicServerConfig
import codes.cy.portfolio.server.publicapi.infrastructure.PublicServerDependencies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private const val COMPRESSION_MIN_SIZE_BYTES: Long = 1024L

fun main() {
    val config = PublicServerConfig.fromEnv()
    val dependencies = PublicServerDependencies.create(config = config)
    embeddedServer(Netty, port = config.port, host = config.host) {
        monitor.subscribe(ApplicationStopped) {
            dependencies.close()
        }
        publicModule(dependencies.modules)
    }.start(wait = true)
}

fun Application.publicModule(modules: PublicAppModules) {
    val logger = LoggerFactory.getLogger("codes.cy.portfolio.server.publicapi")

    install(ContentNegotiation) {
        json(PortfolioJson)
    }
    install(Resources)
    install(Compression) {
        gzip {
            priority = 1.0
            minimumSize(COMPRESSION_MIN_SIZE_BYTES)
        }
        deflate {
            priority = 0.5
            minimumSize(COMPRESSION_MIN_SIZE_BYTES)
        }
    }
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("X-Frame-Options", "DENY")
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        modules.config.corsAllowedOrigins.forEach { origin ->
            allowHost(origin)
        }
        if (modules.config.isDevelopment && modules.config.corsAllowedOrigins.isEmpty()) {
            anyHost()
        }
    }
    install(CallLogging)
    configurePublicStatusPages(isDevelopment = modules.config.isDevelopment, logger = logger)

    routing {
        registerPublicApiRoutes(
            readinessChecker = modules.readinessChecker,
            queryService = modules.queryService,
            analyticsService = modules.analyticsService,
        )
        registerPublicSeoRoutes(
            queryService = modules.queryService,
            siteBaseUrl = modules.config.publicSiteBaseUrl,
            webStaticDir = modules.config.webStaticDir,
        )
    }
}
