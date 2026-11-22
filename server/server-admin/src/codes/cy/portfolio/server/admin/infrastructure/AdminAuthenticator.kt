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
    suspend fun authenticate(authorizationHeader: String?): AdminPrincipal
}

/**
 * Verifies standard `Authorization: Bearer <jwt>` credentials:
 * - Local dev HS256 JWT (`scripts/dev-token.sh`, strictly disabled outside dev/local/test)
 * - Owner-allowlisted OAuth session HS256 JWT (when `ADMIN_SESSION_SECRET` is configured)
 * - Standard OIDC RS256 ID token via JWKS (defaults to Google OIDC when `OAUTH_GOOGLE_CLIENT_ID` is set)
 */
class DefaultAdminAuthenticator(
    private val config: AdminServerConfig,
    jwkProviderOverride: JwkProvider? = null,
) : AdminTokenAuthenticator {

    private val oidcJwkProvider: JwkProvider? = jwkProviderOverride ?: buildOidcJwkProvider(config.oidcJwksUrl)

    override suspend fun authenticate(authorizationHeader: String?): AdminPrincipal {
        val bearerToken = extractBearerToken(authorizationHeader)
            ?: throw UnauthorizedException("Missing Authorization Bearer token.")

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

        if (isOidcConfigured()) {
            return verifyOidcJwt(bearerToken)
        }

        throw UnauthorizedException("Invalid or expired authentication token.")
    }

    private fun isOidcConfigured(): Boolean =
        oidcJwkProvider != null &&
            !config.oidcIssuer.isNullOrBlank() &&
            !config.oidcAudience.isNullOrBlank()

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

    private suspend fun verifyOidcJwt(token: String): AdminPrincipal = withContext(Dispatchers.IO) {
        val provider = oidcJwkProvider
            ?: throw UnauthorizedException("OIDC JWK provider is not configured.")
        val issuer = requireNotNull(config.oidcIssuer).trim().trimEnd('/')
        val audience = requireNotNull(config.oidcAudience).trim()

        val decoded = try {
            val unverified = JWT.decode(token)
            val keyId = unverified.keyId?.takeIf { it.isNotBlank() }
                ?: throw UnauthorizedException("OIDC token is missing key ID (kid).")
            val jwk = provider.get(keyId)
            val publicKey = jwk.publicKey as? RSAPublicKey
                ?: throw UnauthorizedException("OIDC JWK public key is not RSA.")
            JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
                .verify(token)
        } catch (ex: UnauthorizedException) {
            throw ex
        } catch (_: Exception) {
            throw UnauthorizedException("Invalid or expired OIDC token.")
        }

        val email = decoded.getClaim(CLAIM_EMAIL).asString()?.trim()?.takeIf { it.isNotEmpty() }
        if (email != null) {
            val emailVerifiedClaim = decoded.getClaim(CLAIM_EMAIL_VERIFIED)
            if (!emailVerifiedClaim.isNull && !emailVerifiedClaim.isMissing && emailVerifiedClaim.asBoolean() == false) {
                throw ForbiddenException("OIDC account email '$email' is not verified.")
            }
        }

        val identity = email
            ?: decoded.subject?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw UnauthorizedException("OIDC token lacks identity claim.")

        if (identity !in config.allowedOwnerSubjects) {
            throw ForbiddenException("OIDC identity '$identity' is not on owner allowlist.")
        }
        AdminPrincipal(subject = identity, role = ROLE_OWNER, authSource = AUTH_SOURCE_OIDC)
    }

    private fun extractBearerToken(headerValue: String?): String? {
        val trimmed = headerValue?.trim() ?: return null
        if (!trimmed.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            return null
        }
        return trimmed.substring(BEARER_PREFIX.length).trim().takeIf { it.isNotEmpty() }
    }

    private fun buildOidcJwkProvider(jwksUrl: String?): JwkProvider? {
        val cleanUrl = jwksUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return JwkProviderBuilder(URI(cleanUrl).toURL())
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
        const val AUTH_SOURCE_OIDC: String = "oidc"
        private const val BEARER_PREFIX: String = "Bearer "
        private const val CLAIM_ROLE: String = "role"
        private const val CLAIM_EMAIL: String = "email"
        private const val CLAIM_EMAIL_VERIFIED: String = "email_verified"
        private const val JWK_CACHE_SIZE: Long = 10L
        private const val JWK_CACHE_MINUTES: Long = 60L
    }
}
