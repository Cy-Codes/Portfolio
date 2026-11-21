package codes.cy.portfolio.server.publicapi.domain

data class PublicServerConfig(
    val host: String,
    val port: Int,
    val appEnv: String,
    val publicSiteBaseUrl: String,
    val analyticsSalt: String,
    val webStaticDir: String? = null,
    val corsAllowedOrigins: List<String> = emptyList(),
) {
    val isDevelopment: Boolean
        get() = appEnv.trim().lowercase() in DEV_ENVIRONMENTS

    companion object {
        const val DEFAULT_HOST: String = "0.0.0.0"
        const val DEFAULT_PORT: Int = 8080
        const val DEFAULT_ENV: String = "dev"
        const val DEFAULT_SITE_BASE_URL: String = "http://127.0.0.1:8080"
        const val DEFAULT_ANALYTICS_SALT: String = "dev-portfolio-analytics-salt"
        private const val MIN_PORT: Int = 1
        private const val MAX_PORT: Int = 65535
        private val DEV_ENVIRONMENTS: Set<String> = setOf("dev", "development", "local")

        fun fromEnv(env: (String) -> String? = System::getenv): PublicServerConfig {
            val host = env("PUBLIC_SERVER_HOST")?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
            val port = env("PUBLIC_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
            require(port in MIN_PORT..MAX_PORT) {
                "Invalid PUBLIC_SERVER_PORT '$port'. Must be in $MIN_PORT..$MAX_PORT."
            }
            val appEnv = env("APP_ENV")?.takeIf { it.isNotBlank() } ?: DEFAULT_ENV
            val baseUrl = (env("PUBLIC_SITE_BASE_URL")?.takeIf { it.isNotBlank() } ?: DEFAULT_SITE_BASE_URL)
                .trimEnd('/')
            val salt = env("ANALYTICS_DAILY_SALT")?.takeIf { it.isNotBlank() } ?: DEFAULT_ANALYTICS_SALT
            val staticDir = env("WEB_STATIC_DIR")?.takeIf { it.isNotBlank() }
            val origins = env("PUBLIC_CORS_ORIGINS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            return PublicServerConfig(
                host = host,
                port = port,
                appEnv = appEnv,
                publicSiteBaseUrl = baseUrl,
                analyticsSalt = salt,
                webStaticDir = staticDir,
                corsAllowedOrigins = origins,
            )
        }
    }
}
