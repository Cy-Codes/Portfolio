package codes.cy.portfolio.server.admin.api

import codes.cy.portfolio.server.admin.domain.AdminPrincipal
import codes.cy.portfolio.server.admin.infrastructure.AdminTokenAuthenticator
import io.ktor.http.HttpHeaders
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey

private const val HEADER_CF_ACCESS_JWT: String = "Cf-Access-Jwt-Assertion"

val AdminPrincipalKey: AttributeKey<AdminPrincipal> = AttributeKey("PortfolioAdminPrincipal")

private object ProtectedAdminRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Transparent
}

/**
 * Unconditionally authenticates every admin route inside its child routing scope via
 * Cloudflare Access JWT or owner-allowlisted bearer JWT, leaving sibling `/health` probes open.
 */
fun createAdminAuthPlugin(authenticator: AdminTokenAuthenticator): RouteScopedPlugin<Unit> =
    createRouteScopedPlugin(name = "PortfolioAdminAuth") {
        onCall { call ->
            val principal = authenticator.authenticate(
                cfAccessAssertion = call.request.headers[HEADER_CF_ACCESS_JWT],
                authorizationHeader = call.request.headers[HttpHeaders.Authorization],
            )
            call.attributes.put(AdminPrincipalKey, principal)
        }
    }

fun Route.authenticatedAdminScope(
    authenticator: AdminTokenAuthenticator,
    build: Route.() -> Unit,
) {
    val protectedRoute = createChild(ProtectedAdminRouteSelector)
    protectedRoute.install(createAdminAuthPlugin(authenticator))
    protectedRoute.build()
}
