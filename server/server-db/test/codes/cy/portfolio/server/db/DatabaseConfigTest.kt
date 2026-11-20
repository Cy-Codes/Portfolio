package codes.cy.portfolio.server.db.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseConfigTest {
    @Test
    fun `resolves distinct credentials per role from environment`() {
        val env = mapOf(
            "POSTGRES_HOST" to "db.internal",
            "POSTGRES_PORT" to "5433",
            "POSTGRES_DB" to "portfolio_prod",
            "PORTFOLIO_PUBLIC_USER" to "pub_ro",
            "PORTFOLIO_PUBLIC_PASSWORD" to "pub_secret",
            "PORTFOLIO_ADMIN_USER" to "adm_rw",
            "PORTFOLIO_ADMIN_PASSWORD" to "adm_secret",
        )

        val publicConfig = DatabaseConfig.fromEnv(DatabaseRole.PUBLIC) { key -> env[key] }
        assertEquals("jdbc:postgresql://db.internal:5433/portfolio_prod?reWriteBatchedInserts=true", publicConfig.jdbcUrl)
        assertEquals("pub_ro", publicConfig.username)
        assertEquals("pub_secret", publicConfig.password)

        val adminConfig = DatabaseConfig.fromEnv(DatabaseRole.ADMIN) { key -> env[key] }
        assertEquals("adm_rw", adminConfig.username)
        assertEquals("adm_secret", adminConfig.password)
    }

    @Test
    fun `rejects out of range port numbers`() {
        val env = mapOf("POSTGRES_PORT" to "99999")
        assertFailsWith<IllegalArgumentException> {
            DatabaseConfig.fromEnv(DatabaseRole.PUBLIC) { key -> env[key] }
        }
    }
}
