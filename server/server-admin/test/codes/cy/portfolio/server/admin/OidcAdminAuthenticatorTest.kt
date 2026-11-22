package codes.cy.portfolio.server.admin

import codes.cy.portfolio.server.admin.domain.AdminServerConfig
import codes.cy.portfolio.server.admin.infrastructure.DefaultAdminAuthenticator
import codes.cy.portfolio.server.db.errors.ForbiddenException
import codes.cy.portfolio.server.db.errors.UnauthorizedException
import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OidcAdminAuthenticatorTest {

    @Test
    fun `AdminServerConfig auto-defaults Google OIDC endpoints when OAUTH_GOOGLE_CLIENT_ID is set`() {
        val env = mapOf(
            "APP_ENV" to "prod",
            "OAUTH_GOOGLE_CLIENT_ID" to "12345.apps.googleusercontent.com",
            "ADMIN_ALLOWED_SUBJECTS" to "owner@example.com",
        )
        val config = AdminServerConfig.fromEnv { env[it] }
        assertEquals(AdminServerConfig.GOOGLE_OIDC_JWKS_URL, config.oidcJwksUrl)
        assertEquals(AdminServerConfig.GOOGLE_OIDC_ISSUER, config.oidcIssuer)
        assertEquals("12345.apps.googleusercontent.com", config.oidcAudience)
        assertEquals(setOf("owner@example.com"), config.allowedOwnerSubjects)
    }

    @Test
    fun `OIDC RS256 bearer token authenticates verified allowlisted owner and rejects invalid or unverified tokens`() = runTest {
        val keyPair = generateRsaKeyPair()
        val publicKey = keyPair.public as RSAPublicKey
        val privateKey = keyPair.private as RSAPrivateKey
        val keyId = "test-oidc-key-1"

        val jwkProvider = JwkProvider { requestedKid ->
            require(requestedKid == keyId) { "Unknown kid: $requestedKid" }
            rsaPublicKeyToJwk(keyId, publicKey)
        }

        val config = AdminServerConfig(
            host = "0.0.0.0",
            port = 8081,
            appEnv = "prod",
            devJwtSecret = AdminServerConfig.DEFAULT_DEV_JWT_SECRET,
            adminSessionSecret = null,
            allowedOwnerSubjects = setOf("owner@example.com"),
            oidcJwksUrl = AdminServerConfig.GOOGLE_OIDC_JWKS_URL,
            oidcIssuer = AdminServerConfig.GOOGLE_OIDC_ISSUER,
            oidcAudience = "client-id.apps.googleusercontent.com",
            oauthGithubClientId = null,
            oauthGoogleClientId = "client-id.apps.googleusercontent.com",
        )
        val authenticator = DefaultAdminAuthenticator(config = config, jwkProviderOverride = jwkProvider)

        val validToken = signRsaToken(
            privateKey = privateKey,
            keyId = keyId,
            issuer = AdminServerConfig.GOOGLE_OIDC_ISSUER,
            audience = "client-id.apps.googleusercontent.com",
            email = "owner@example.com",
            emailVerified = true,
        )
        val principal = authenticator.authenticate("Bearer $validToken")
        assertEquals("owner@example.com", principal.subject)
        assertEquals(DefaultAdminAuthenticator.ROLE_OWNER, principal.role)
        assertEquals(DefaultAdminAuthenticator.AUTH_SOURCE_OIDC, principal.authSource)

        val unverifiedEmailToken = signRsaToken(
            privateKey = privateKey,
            keyId = keyId,
            issuer = AdminServerConfig.GOOGLE_OIDC_ISSUER,
            audience = "client-id.apps.googleusercontent.com",
            email = "owner@example.com",
            emailVerified = false,
        )
        assertFailsWith<ForbiddenException> {
            authenticator.authenticate("Bearer $unverifiedEmailToken")
        }

        val strangerToken = signRsaToken(
            privateKey = privateKey,
            keyId = keyId,
            issuer = AdminServerConfig.GOOGLE_OIDC_ISSUER,
            audience = "client-id.apps.googleusercontent.com",
            email = "stranger@example.com",
            emailVerified = true,
        )
        assertFailsWith<ForbiddenException> {
            authenticator.authenticate("Bearer $strangerToken")
        }

        val wrongAudienceToken = signRsaToken(
            privateKey = privateKey,
            keyId = keyId,
            issuer = AdminServerConfig.GOOGLE_OIDC_ISSUER,
            audience = "other-client.apps.googleusercontent.com",
            email = "owner@example.com",
            emailVerified = true,
        )
        assertFailsWith<UnauthorizedException> {
            authenticator.authenticate("Bearer $wrongAudienceToken")
        }
    }

    private fun generateRsaKeyPair() =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun rsaPublicKeyToJwk(keyId: String, publicKey: RSAPublicKey): Jwk {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val attributes = mapOf<String, Any>(
            "n" to encoder.encodeToString(publicKey.modulus.toByteArray()),
            "e" to encoder.encodeToString(publicKey.publicExponent.toByteArray()),
        )
        return Jwk(keyId, "RSA", "RS256", "sig", emptyList(), null, emptyList(), null, attributes)
    }

    private fun signRsaToken(
        privateKey: RSAPrivateKey,
        keyId: String,
        issuer: String,
        audience: String,
        email: String,
        emailVerified: Boolean,
    ): String =
        JWT.create()
            .withKeyId(keyId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject("google-sub-123")
            .withClaim("email", email)
            .withClaim("email_verified", emailVerified)
            .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
            .sign(Algorithm.RSA256(null, privateKey))
}
