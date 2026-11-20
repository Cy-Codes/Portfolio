package codes.cy.portfolio.server.db.config

enum class DatabaseRole {
    PUBLIC,
    ADMIN,
    MIGRATOR,
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    val connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_HOST: String = "127.0.0.1"
        const val DEFAULT_PORT: Int = 5432
        const val DEFAULT_DB: String = "portfolio"
        const val DEFAULT_MAX_POOL_SIZE: Int = 10
        const val DEFAULT_CONNECTION_TIMEOUT_MS: Long = 10_000L
        private const val MIN_PORT: Int = 1
        private const val MAX_PORT: Int = 65535

        fun fromEnv(
            role: DatabaseRole,
            env: (String) -> String? = System::getenv,
        ): DatabaseConfig {
            val host = env("POSTGRES_HOST")?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
            val port = env("POSTGRES_PORT")?.toIntOrNull() ?: DEFAULT_PORT
            require(port in MIN_PORT..MAX_PORT) {
                "Invalid POSTGRES_PORT '$port'. Must be in $MIN_PORT..$MAX_PORT."
            }
            val database = env("POSTGRES_DB")?.takeIf { it.isNotBlank() } ?: DEFAULT_DB

            val (userKey, defaultUser, passwordKey, defaultPassword) = when (role) {
                DatabaseRole.PUBLIC -> RoleEnvDefaults(
                    userKey = "PORTFOLIO_PUBLIC_USER",
                    defaultUser = "portfolio_public",
                    passwordKey = "PORTFOLIO_PUBLIC_PASSWORD",
                    defaultPassword = "dev_public_change_me",
                )
                DatabaseRole.ADMIN -> RoleEnvDefaults(
                    userKey = "PORTFOLIO_ADMIN_USER",
                    defaultUser = "portfolio_admin",
                    passwordKey = "PORTFOLIO_ADMIN_PASSWORD",
                    defaultPassword = "dev_admin_change_me",
                )
                DatabaseRole.MIGRATOR -> RoleEnvDefaults(
                    userKey = "PORTFOLIO_MIGRATOR_USER",
                    defaultUser = "portfolio_migrator",
                    passwordKey = "PORTFOLIO_MIGRATOR_PASSWORD",
                    defaultPassword = "dev_migrator_change_me",
                )
            }

            val jdbcUrl = env("JDBC_DATABASE_URL")?.takeIf { it.isNotBlank() }
                ?: "jdbc:postgresql://$host:$port/$database?reWriteBatchedInserts=true"
            val username = env(userKey)?.takeIf { it.isNotBlank() } ?: defaultUser
            val password = env(passwordKey)?.takeIf { it.isNotBlank() } ?: defaultPassword
            val poolSize = env("DB_MAX_POOL_SIZE")?.toIntOrNull() ?: DEFAULT_MAX_POOL_SIZE

            return DatabaseConfig(
                jdbcUrl = jdbcUrl,
                username = username,
                password = password,
                maximumPoolSize = poolSize,
            )
        }
    }

    private data class RoleEnvDefaults(
        val userKey: String,
        val defaultUser: String,
        val passwordKey: String,
        val defaultPassword: String,
    )
}
