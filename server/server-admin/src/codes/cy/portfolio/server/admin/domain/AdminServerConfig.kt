package codes.cy.portfolio.server.admin.domain

data class AdminServerConfig(
    val host: String,
    val port: Int,
    val appEnv: String,
    val devJwtSecret: String,
    val adminSessionSecret: String?,
    val allowedOwnerSubjects: Set<String>,
    val oidcJwksUrl: String?,
    val oidcIssuer: String?,
    val oidcAudience: String?,
    val oauthGithubClientId: String?,
    val oauthGoogleClientId: String?,
    val corsAllowedOrigins: List<String> = emptyList(),
) {
    val isDevelopment: Boolean
        get() = appEnv.trim().lowercase() in DEV_ENVIRONMENTS

    val isDevAuthAllowed: Boolean
        get() = appEnv.trim().lowercase() in DEV_AUTH_ENVIRONMENTS

    companion object {
        const val DEFAULT_HOST: String = "127.0.0.1"
        const val DEFAULT_PORT: Int = 8081
        const val DEFAULT_ENV: String = "dev"
        const val DEFAULT_DEV_JWT_SECRET: String = "dev-only-portfolio-hs256-secret-minimum-32-bytes"
        const val DEFAULT_OWNER_SUBJECT: String = "dev-owner"
        const val GOOGLE_OIDC_JWKS_URL: String = "https://www.googleapis.com/oauth2/v3/certs"
        const val GOOGLE_OIDC_ISSUER: String = "https://accounts.google.com"
        private const val MIN_PORT: Int = 1
        private const val MAX_PORT: Int = 65535
        private val DEV_ENVIRONMENTS: Set<String> = setOf("dev", "development", "local", "test")
        private val DEV_AUTH_ENVIRONMENTS: Set<String> = setOf("dev", "development", "local", "test")

        fun fromEnv(env: (String) -> String? = System::getenv): AdminServerConfig {
            val host = env("ADMIN_SERVER_HOST")?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
            val port = env("ADMIN_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
            require(port in MIN_PORT..MAX_PORT) {
                "Invalid ADMIN_SERVER_PORT '$port'. Must be in $MIN_PORT..$MAX_PORT."
            }
            val appEnv = env("APP_ENV")?.takeIf { it.isNotBlank() } ?: DEFAULT_ENV
            val devSecret = env("DEV_JWT_SECRET")?.takeIf { it.isNotBlank() } ?: DEFAULT_DEV_JWT_SECRET
            val sessionSecret = env("ADMIN_SESSION_SECRET")?.takeIf { it.isNotBlank() }
            val allowedSubjects = env("ADMIN_ALLOWED_SUBJECTS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: setOf(DEFAULT_OWNER_SUBJECT)
            val origins = env("ADMIN_CORS_ORIGINS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val googleClientId = env("OAUTH_GOOGLE_CLIENT_ID")?.takeIf { it.isNotBlank() }
            val oidcJwksUrl = env("OIDC_JWKS_URL")?.takeIf { it.isNotBlank() }
                ?: googleClientId?.let { GOOGLE_OIDC_JWKS_URL }
            val oidcIssuer = env("OIDC_ISSUER")?.takeIf { it.isNotBlank() }
                ?: googleClientId?.let { GOOGLE_OIDC_ISSUER }
            val oidcAudience = env("OIDC_AUDIENCE")?.takeIf { it.isNotBlank() }
                ?: googleClientId

            return AdminServerConfig(
                host = host,
                port = port,
                appEnv = appEnv,
                devJwtSecret = devSecret,
                adminSessionSecret = sessionSecret,
                allowedOwnerSubjects = allowedSubjects,
                oidcJwksUrl = oidcJwksUrl,
                oidcIssuer = oidcIssuer,
                oidcAudience = oidcAudience,
                oauthGithubClientId = env("OAUTH_GITHUB_CLIENT_ID")?.takeIf { it.isNotBlank() },
                oauthGoogleClientId = googleClientId,
                corsAllowedOrigins = origins,
            )
        }
    }
}
