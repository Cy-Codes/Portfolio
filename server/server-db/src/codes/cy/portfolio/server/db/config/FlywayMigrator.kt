package codes.cy.portfolio.server.db.config

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/**
 * One-shot Flyway schema migrator pointing exclusively at classpath SQL deltas.
 * Intended for test harnesses and dedicated migration jobs, never routine app startup.
 */
object FlywayMigrator {
    private const val MIGRATIONS_LOCATION: String = "classpath:db/migrations"
    private const val DEFAULT_SCHEMA: String = "portfolio"
    private val MANAGED_SCHEMAS: Array<String> = arrayOf("portfolio", "analytics")

    fun executeMigrate(config: DatabaseConfig): MigrateResult =
        Flyway.configure()
            .dataSource(config.jdbcUrl, config.username, config.password)
            .schemas(*MANAGED_SCHEMAS)
            .defaultSchema(DEFAULT_SCHEMA)
            .createSchemas(true)
            .locations(MIGRATIONS_LOCATION)
            .baselineOnMigrate(false)
            .load()
            .migrate()
}
