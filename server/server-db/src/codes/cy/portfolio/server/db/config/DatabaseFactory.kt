package codes.cy.portfolio.server.db.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

data class SchemaReadinessStatus(
    val ready: Boolean,
    val appliedMigrationCount: Int,
)

/**
 * Owns the HikariCP connection pool and bridges Exposed 1.0 JDBC transactions
 * onto [Dispatchers.IO] without starving Ktor event-loop threads.
 */
class DatabaseFactory private constructor(
    private val dataSource: HikariDataSource,
    private val database: Database,
) : AutoCloseable {

    suspend fun <T> dbQuery(block: suspend JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction(db = database) {
                block()
            }
        }

    suspend fun checkSchemaReady(): SchemaReadinessStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(READINESS_SQL).use { stmt ->
                        stmt.executeQuery().use { rs ->
                            val count = if (rs.next()) rs.getInt(1) else 0
                            SchemaReadinessStatus(
                                ready = count > 0,
                                appliedMigrationCount = count,
                            )
                        }
                    }
                }
            }.getOrElse {
                SchemaReadinessStatus(ready = false, appliedMigrationCount = 0)
            }
        }

    override fun close() {
        dataSource.close()
    }

    companion object {
        private const val POSTGRES_DRIVER_CLASS: String = "org.postgresql.Driver"
        private const val TRANSACTION_ISOLATION_REPEATABLE_READ: String = "TRANSACTION_REPEATABLE_READ"
        private const val READINESS_SQL: String =
            "SELECT COUNT(*) FROM portfolio.flyway_schema_history WHERE success = TRUE"

        fun connect(config: DatabaseConfig): DatabaseFactory {
            val hikariConfig = HikariConfig().apply {
                driverClassName = POSTGRES_DRIVER_CLASS
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.maximumPoolSize
                connectionTimeout = config.connectionTimeoutMs
                isAutoCommit = false
                transactionIsolation = TRANSACTION_ISOLATION_REPEATABLE_READ
                validate()
            }
            val dataSource = HikariDataSource(hikariConfig)
            val database = Database.connect(dataSource)
            return DatabaseFactory(dataSource = dataSource, database = database)
        }
    }
}
