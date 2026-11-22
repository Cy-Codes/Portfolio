package codes.cy.portfolio.server.publicapi.infrastructure

import codes.cy.portfolio.server.db.config.DatabaseConfig
import codes.cy.portfolio.server.db.config.DatabaseFactory
import codes.cy.portfolio.server.db.config.DatabaseRole
import codes.cy.portfolio.server.db.repositories.ExposedAnalyticsRepository
import codes.cy.portfolio.server.db.repositories.ExposedDocumentRepository
import codes.cy.portfolio.server.db.repositories.ExposedPortfolioRepository
import codes.cy.portfolio.server.db.repositories.ExposedProjectRepository
import codes.cy.portfolio.server.db.repositories.ExposedWriteupRepository
import codes.cy.portfolio.server.db.storage.BlobStorageReader
import codes.cy.portfolio.server.db.storage.GcsBlobStorage
import codes.cy.portfolio.server.db.storage.GcsBlobStorageConfig
import codes.cy.portfolio.server.publicapi.application.PublicAnalyticsCommandService
import codes.cy.portfolio.server.publicapi.application.PublicAppModules
import codes.cy.portfolio.server.publicapi.application.PublicPortfolioQueryService
import codes.cy.portfolio.server.publicapi.application.ReadinessChecker
import codes.cy.portfolio.server.publicapi.domain.PublicServerConfig
import java.time.Clock

/**
 * Runtime wiring for `server/server-public` using least-privilege [DatabaseRole.PUBLIC]
 * and read-only [BlobStorageReader].
 */
class PublicServerDependencies(
    val modules: PublicAppModules,
    private val dbFactory: DatabaseFactory,
    private val blobStorageReader: BlobStorageReader,
) : AutoCloseable {

    override fun close() {
        runCatching { blobStorageReader.close() }
        runCatching { dbFactory.close() }
    }

    companion object {
        fun create(
            config: PublicServerConfig = PublicServerConfig.fromEnv(),
            clock: Clock = Clock.systemUTC(),
        ): PublicServerDependencies {
            val dbFactory = DatabaseFactory.connect(DatabaseConfig.fromEnv(DatabaseRole.PUBLIC))
            val blobStorageReader: BlobStorageReader = GcsBlobStorage(GcsBlobStorageConfig.fromEnv(), clock)

            val portfolioRepo = ExposedPortfolioRepository(dbFactory, clock)
            val projectRepo = ExposedProjectRepository(dbFactory, clock)
            val writeupRepo = ExposedWriteupRepository(dbFactory, clock)
            val documentRepo = ExposedDocumentRepository(dbFactory, clock)
            val analyticsRepo = ExposedAnalyticsRepository(dbFactory, clock)

            val modules = PublicAppModules(
                config = config,
                readinessChecker = ReadinessChecker { dbFactory.checkSchemaReady() },
                queryService = PublicPortfolioQueryService(
                    portfolioRepo = portfolioRepo,
                    projectRepo = projectRepo,
                    writeupRepo = writeupRepo,
                    documentRepo = documentRepo,
                    blobStorageReader = blobStorageReader,
                    siteBaseUrl = config.publicSiteBaseUrl,
                ),
                analyticsService = PublicAnalyticsCommandService(
                    analyticsRepo = analyticsRepo,
                    analyticsSalt = config.analyticsSalt,
                    clock = clock,
                ),
            )
            return PublicServerDependencies(
                modules = modules,
                dbFactory = dbFactory,
                blobStorageReader = blobStorageReader,
            )
        }
    }
}
