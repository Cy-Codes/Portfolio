package codes.cy.portfolio.server.admin.infrastructure

import codes.cy.portfolio.server.admin.application.AdminAppModules
import codes.cy.portfolio.server.admin.application.AdminDocumentCommandService
import codes.cy.portfolio.server.admin.application.AdminPortfolioCommandService
import codes.cy.portfolio.server.admin.application.AdminPortfolioQueryService
import codes.cy.portfolio.server.admin.application.AdminStatsQueryService
import codes.cy.portfolio.server.admin.application.ReadinessChecker
import codes.cy.portfolio.server.admin.domain.AdminServerConfig
import codes.cy.portfolio.server.db.config.DatabaseConfig
import codes.cy.portfolio.server.db.config.DatabaseFactory
import codes.cy.portfolio.server.db.config.DatabaseRole
import codes.cy.portfolio.server.db.repositories.ExposedAnalyticsRepository
import codes.cy.portfolio.server.db.repositories.ExposedDocumentRepository
import codes.cy.portfolio.server.db.repositories.ExposedPortfolioRepository
import codes.cy.portfolio.server.db.repositories.ExposedProjectRepository
import codes.cy.portfolio.server.db.repositories.ExposedWriteupRepository
import codes.cy.portfolio.server.db.storage.BlobStorage
import codes.cy.portfolio.server.db.storage.BlobStorageConfig
import codes.cy.portfolio.server.db.storage.S3BlobStorage
import java.time.Clock

/**
 * Runtime wiring for `server/server-admin` using least-privilege DML [DatabaseRole.ADMIN]
 * and full read-write [BlobStorage].
 */
class AdminServerDependencies(
    val modules: AdminAppModules,
    private val dbFactory: DatabaseFactory,
    private val blobStorage: BlobStorage,
) : AutoCloseable {

    override fun close() {
        runCatching { blobStorage.close() }
        runCatching { dbFactory.close() }
    }

    companion object {
        fun create(
            config: AdminServerConfig = AdminServerConfig.fromEnv(),
            clock: Clock = Clock.systemUTC(),
        ): AdminServerDependencies {
            val dbFactory = DatabaseFactory.connect(DatabaseConfig.fromEnv(DatabaseRole.ADMIN))
            val blobStorage: BlobStorage = S3BlobStorage(BlobStorageConfig.fromEnv(), clock)

            val portfolioRepo = ExposedPortfolioRepository(dbFactory, clock)
            val projectRepo = ExposedProjectRepository(dbFactory, clock)
            val writeupRepo = ExposedWriteupRepository(dbFactory, clock)
            val documentRepo = ExposedDocumentRepository(dbFactory, clock)
            val analyticsRepo = ExposedAnalyticsRepository(dbFactory, clock)

            val modules = AdminAppModules(
                config = config,
                authenticator = DefaultAdminAuthenticator(config),
                readinessChecker = ReadinessChecker { dbFactory.checkSchemaReady() },
                queryService = AdminPortfolioQueryService(
                    portfolioRepo = portfolioRepo,
                    projectRepo = projectRepo,
                    writeupRepo = writeupRepo,
                    documentRepo = documentRepo,
                ),
                commandService = AdminPortfolioCommandService(
                    portfolioRepo = portfolioRepo,
                    projectRepo = projectRepo,
                    writeupRepo = writeupRepo,
                ),
                documentService = AdminDocumentCommandService(
                    documentRepo = documentRepo,
                    blobStorage = blobStorage,
                ),
                statsService = AdminStatsQueryService(analyticsRepo = analyticsRepo),
            )
            return AdminServerDependencies(
                modules = modules,
                dbFactory = dbFactory,
                blobStorage = blobStorage,
            )
        }
    }
}
