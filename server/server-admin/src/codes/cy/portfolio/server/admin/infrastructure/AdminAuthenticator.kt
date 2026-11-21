package codes.cy.portfolio.server.admin.infrastructure

import codes.cy.portfolio.server.admin.domain.AdminPrincipal
import codes.cy.portfolio.server.admin.domain.AdminServerConfig
import codes.cy.portfolio.server.db.errors.ForbiddenException
import codes.cy.portfolio.server.db.errors.UnauthorizedException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AdminTokenAuthenticator {
    suspend fun authenticate(
        cfAccessAssertion: String?,
        authorizationHeader: String?,
    ): AdminPrincipal
}

/**
 * Verifies Cloudflare Access RS256 assertions in production or HS256 bearer JWTs
 * (local `dev-token.sh` strictly disabled outside dev/local/test environments, or
 * owner-allowlisted OAuth session tokens).
 */
class DefaultAdminAuthenticator(
    private val config: AdminServerConfig,
) : AdminTokenAuthenticator {

    private val cfJwkProvider: JwkProvider? = buildCloudflareJwkProvider(config.cfAccessTeamDomain)

    override suspend fun authenticate(
        cfAccessAssertion: String?,
        authorizationHeader: String?,
    ): AdminPrincipal {
        val cfToken = cfAccessAssertion?.trim()?.takeIf { it.isNotEmpty() }
        if (cfToken != null && !config.cfAccessTeamDomain.isNullOrBlank() && !config.cfAccessAudience.isNullOrBlank()) {
            return verifyCloudflareAccessJwt(cfToken)
        }

        val bearerToken = extractBearerToken(authorizationHeader)
            ?: throw UnauthorizedException("Missing bearer token or Cloudflare Access assertion.")

        if (config.isDevAuthAllowed) {
            verifyHs256Token(
                token = bearerToken,
                secret = config.devJwtSecret,
                expectedIssuer = DEV_ISSUER,
                authSource = AUTH_SOURCE_DEV_JWT,
            )?.let { return it }
        }

        val sessionSecret = config.adminSessionSecret
        if (!sessionSecret.isNullOrBlank()) {
            verifyHs256Token(
                token = bearerToken,
                secret = sessionSecret,
                expectedIssuer = OAUTH_SESSION_ISSUER,
                authSource = AUTH_SOURCE_OAUTH_SESSION,
            )?.let { return it }
        }

        throw UnauthorizedException("Invalid or expired authentication token.")
    }

    private fun verifyHs256Token(
        token: String,
        secret: String,
        expectedIssuer: String,
        authSource: String,
    ): AdminPrincipal? {
        val decoded: DecodedJWT = try {
            JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(expectedIssuer)
                .withAudience(ADMIN_AUDIENCE)
                .build()
                .verify(token)
        } catch (_: JWTVerificationException) {
            return null
        }

        val subject = decoded.subject?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw UnauthorizedException("Token subject claim is missing.")
        val role = decoded.getClaim(CLAIM_ROLE).asString()?.trim()?.takeIf { it.isNotEmpty() } ?: ROLE_VIEWER

        if (role != ROLE_OWNER || subject !in config.allowedOwnerSubjects) {
            throw ForbiddenException("Subject '$subject' with role '$role' is not authorized as portfolio owner.")
        }
        return AdminPrincipal(subject = subject, role = role, authSource = authSource)
    }

    private suspend fun verifyCloudflareAccessJwt(token: String): AdminPrincipal = withContext(Dispatchers.IO) {
        val provider = cfJwkProvider
            ?: throw UnauthorizedException("Cloudflare Access JWK provider is not configured.")
        val teamDomain = requireNotNull(config.cfAccessTeamDomain).trim().trimEnd('/')
        val audience = requireNotNull(config.cfAccessAudience).trim()

        val decoded = try {
            val unverified = JWT.decode(token)
            val jwk = provider.get(unverified.keyId)
            val publicKey = jwk.publicKey as? RSAPublicKey
                ?: throw UnauthorizedException("Cloudflare Access public key is not RSA.")
            JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer("https://$teamDomain")
                .withAudience(audience)
                .build()
                .verify(token)
        } catch (ex: UnauthorizedException) {
            throw ex
        } catch (_: Exception) {
            throw UnauthorizedException("Invalid or expired Cloudflare Access assertion.")
        }

        val identity = decoded.getClaim(CLAIM_EMAIL).asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: decoded.subject?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw UnauthorizedException("Cloudflare Access token lacks identity claim.")

        if (identity !in config.allowedOwnerSubjects) {
            throw ForbiddenException("Cloudflare Access identity '$identity' is not on owner allowlist.")
        }
        AdminPrincipal(subject = identity, role = ROLE_OWNER, authSource = AUTH_SOURCE_CLOUDFLARE_ACCESS)
    }

    private fun extractBearerToken(headerValue: String?): String? {
        val trimmed = headerValue?.trim() ?: return null
        if (!trimmed.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            return null
        }
        return trimmed.substring(BEARER_PREFIX.length).trim().takeIf { it.isNotEmpty() }
    }

    private fun buildCloudflareJwkProvider(teamDomain: String?): JwkProvider? {
        val cleanDomain = teamDomain?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        val certsUrl = URI("https://$cleanDomain/cdn-cgi/access/certs").toURL()
        return JwkProviderBuilder(certsUrl)
            .cached(JWK_CACHE_SIZE, JWK_CACHE_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    companion object {
        const val DEV_ISSUER: String = "portfolio-dev"
        const val OAUTH_SESSION_ISSUER: String = "portfolio-admin-oauth"
        const val ADMIN_AUDIENCE: String = "portfolio-admin"
        const val ROLE_OWNER: String = "owner"
        const val ROLE_VIEWER: String = "viewer"
        const val AUTH_SOURCE_DEV_JWT: String = "dev-jwt"
        const val AUTH_SOURCE_OAUTH_SESSION: String = "oauth-session"
        const val AUTH_SOURCE_CLOUDFLARE_ACCESS: String = "cloudflare-access"
        private const val BEARER_PREFIX: String = "Bearer "
        private const val CLAIM_ROLE: String = "role"
        private const val CLAIM_EMAIL: String = "email"
        private const val JWK_CACHE_SIZE: Long = 10L
        private const val JWK_CACHE_MINUTES: Long = 60L
    }
}
