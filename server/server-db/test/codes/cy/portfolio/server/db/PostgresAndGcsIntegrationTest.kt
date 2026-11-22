package codes.cy.portfolio.server.db

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.UpsertProjectRequest
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.server.db.config.DatabaseConfig
import codes.cy.portfolio.server.db.config.DatabaseFactory
import codes.cy.portfolio.server.db.config.FlywayMigrator
import codes.cy.portfolio.server.db.errors.toApiError
import codes.cy.portfolio.server.db.repositories.ExposedAnalyticsRepository
import codes.cy.portfolio.server.db.repositories.ExposedDocumentRepository
import codes.cy.portfolio.server.db.repositories.ExposedPortfolioRepository
import codes.cy.portfolio.server.db.repositories.ExposedProjectRepository
import codes.cy.portfolio.server.db.repositories.ExposedWriteupRepository
import codes.cy.portfolio.server.db.storage.GcsBlobStorage
import codes.cy.portfolio.server.db.storage.GcsBlobStorageConfig
import codes.cy.portfolio.server.db.storage.MimeMagicBytes
import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.StorageOptions
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class FakeGcsTestContainer : GenericContainer<FakeGcsTestContainer>(
    DockerImageName.parse("fsouza/fake-gcs-server:1.52.2"),
)

@Testcontainers
class PostgresAndGcsIntegrationTest {

    @Test
    fun `public role reads published content, inserts analytics without RETURNING, and blocks privileged mutations`() = runTest {
        val publicFactory = DatabaseFactory.connect(
            DatabaseConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = PUBLIC_ROLE,
                password = ROLE_PASSWORD,
            ),
        )
        publicFactory.use { factory ->
            assertTrue(factory.checkSchemaReady().ready)

            val portfolioRepo = ExposedPortfolioRepository(factory)
            assertNotNull(portfolioRepo.getPortfolioOverview())

            val writeupRepo = ExposedWriteupRepository(factory)
            val publishedPage = writeupRepo.getWriteups(
                options = QueryOptions(),
                publishedOnly = true,
                tag = "ktor",
                searchQuery = "postgres",
            )
            assertTrue(publishedPage.items.isNotEmpty())
            assertTrue(publishedPage.items.all { it.status == WriteupStatus.PUBLISHED })

            val analyticsRepo = ExposedAnalyticsRepository(factory)
            analyticsRepo.recordEvent(
                request = TrackEventRequest(
                    eventType = AnalyticsEventType.PAGE_VIEW,
                    path = "/projects/test-integration",
                    entityId = null,
                    referrerHost = "kotlinlang.org",
                    platform = ClientPlatform.WEB,
                    viewportTier = ViewportTier.LARGE,
                ),
                visitorHash = "a".repeat(64),
            )

            // Verify portfolio_public cannot UPDATE portfolio tables or SELECT from analytics.events (SQLState 42501)
            DriverManager.getConnection(postgres.jdbcUrl, PUBLIC_ROLE, ROLE_PASSWORD).use { conn ->
                val updateErr = assertFailsWith<SQLException> {
                    conn.createStatement().use { st ->
                        st.executeUpdate("UPDATE portfolio.writeups SET title = 'blocked'")
                    }
                }
                assertEquals(PG_INSUFFICIENT_PRIVILEGE, updateErr.sqlState)

                val selectAnalyticsErr = assertFailsWith<SQLException> {
                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT count(*) FROM analytics.events")
                    }
                }
                assertEquals(PG_INSUFFICIENT_PRIVILEGE, selectAnalyticsErr.sqlState)
            }
        }
    }

    @Test
    fun `admin role executes CRUD, maps 23505 and 23503 to Conflict, and cannot execute DDL`() = runTest {
        val adminFactory = DatabaseFactory.connect(
            DatabaseConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = ADMIN_ROLE,
                password = ROLE_PASSWORD,
            ),
        )
        adminFactory.use { factory ->
            assertTrue(factory.checkSchemaReady().ready)

            val projectRepo = ExposedProjectRepository(factory)
            val created = projectRepo.createProject(
                UpsertProjectRequest(
                    slug = "tc-integration-project",
                    title = "Testcontainers Project",
                    summary = "Integration verification project",
                    caseStudyMarkdown = "# Case Study\nTested with Testcontainers.",
                    status = ProjectStatus.ACTIVE,
                    isFeatured = true,
                ),
            )
            assertEquals("tc-integration-project", created.slug)

            val duplicateFailure = runCatching {
                projectRepo.createProject(
                    UpsertProjectRequest(
                        slug = "tc-integration-project",
                        title = "Duplicate Slug",
                        summary = "Should fail with 23505",
                        caseStudyMarkdown = "# Duplicate",
                    ),
                )
            }.exceptionOrNull()
            assertNotNull(duplicateFailure)
            assertIs<ApiError.Conflict>(duplicateFailure.toApiError(isDevelopment = false))

            val fkFailure = runCatching {
                projectRepo.createProject(
                    UpsertProjectRequest(
                        slug = "tc-missing-cover-fk",
                        title = "Broken Cover FK",
                        summary = "Should fail with 23503",
                        caseStudyMarkdown = "# FK",
                        coverDocumentId = "99999999-9999-9999-9999-999999999999",
                    ),
                )
            }.exceptionOrNull()
            assertNotNull(fkFailure)
            assertIs<ApiError.Conflict>(fkFailure.toApiError(isDevelopment = false))

            assertTrue(projectRepo.deleteProject(Uuid.parse(created.id)))
            assertNull(projectRepo.findProjectBySlug("tc-integration-project"))

            DriverManager.getConnection(postgres.jdbcUrl, ADMIN_ROLE, ROLE_PASSWORD).use { conn ->
                val ddlErr = assertFailsWith<SQLException> {
                    conn.createStatement().use { st ->
                        st.execute("CREATE TABLE portfolio.forbidden_ddl_test (id INT)")
                    }
                }
                assertEquals(PG_INSUFFICIENT_PRIVILEGE, ddlErr.sqlState)
            }
        }
    }

    @Test
    fun `gcs blob storage supports presignPut, magic-byte verifyObject, presignGet, and deleteObject`() = runTest {
        val gcsEndpoint = "http://${fakeGcs.host}:${fakeGcs.getMappedPort(FAKE_GCS_PORT)}"
        val storageConfig = GcsBlobStorageConfig(
            bucket = TEST_BUCKET,
            emulatorHost = gcsEndpoint,
            publicEmulatorHost = gcsEndpoint,
            projectId = GcsBlobStorageConfig.DEFAULT_LOCAL_PROJECT_ID,
        )

        GcsBlobStorage(storageConfig).use { storage ->
            val key = "documents/${Uuid.random()}/tc-sample.pdf"
            val presigned = storage.presignPut(storageKey = key, contentType = MimeMagicBytes.MIME_PDF)
            val pdfBytes = "%PDF-1.4\n%Testcontainers PDF sample\n".toByteArray(StandardCharsets.US_ASCII)

            val httpClient = HttpClient.newHttpClient()
            val putRequestBuilder = HttpRequest.newBuilder(URI.create(presigned.url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(pdfBytes))
                .header("Content-Type", MimeMagicBytes.MIME_PDF)
            presigned.requiredHeaders
                .filterKeys { !it.equals("Host", ignoreCase = true) && !it.equals("Content-Type", ignoreCase = true) }
                .forEach { (name, value) -> putRequestBuilder.header(name, value) }

            val putResponse = httpClient.send(putRequestBuilder.build(), HttpResponse.BodyHandlers.discarding())
            assertTrue(putResponse.statusCode() in 200..299, "Expected 2xx from fake-gcs PUT, got ${putResponse.statusCode()}")

            val head = storage.verifyObject(key)
            assertNotNull(head)
            assertEquals(pdfBytes.size.toLong(), head.sizeBytes)
            assertTrue(MimeMagicBytes.detect(head.leadingBytes, MimeMagicBytes.MIME_PDF).isAllowed)
            assertFalse(MimeMagicBytes.detect(head.leadingBytes, MimeMagicBytes.MIME_PNG).isAllowed)

            val getUrl = storage.presignGet(
                storageKey = key,
                downloadFilename = "tc-sample.pdf",
                contentType = MimeMagicBytes.MIME_PDF,
            )
            val getResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(getUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            assertEquals(200, getResponse.statusCode())
            assertEquals(pdfBytes.toList(), getResponse.body().toList())

            storage.deleteObject(key)
            assertNull(storage.verifyObject(key))
        }
    }

    companion object {
        private const val MIGRATOR_ROLE: String = "portfolio_migrator"
        private const val ADMIN_ROLE: String = "portfolio_admin"
        private const val PUBLIC_ROLE: String = "portfolio_public"
        private const val ROLE_PASSWORD: String = "tc_role_secret_pw"
        private const val PG_INSUFFICIENT_PRIVILEGE: String = "42501"
        private const val FAKE_GCS_PORT: Int = 4443
        private const val TEST_BUCKET: String = "portfolio-tc-assets"

        @Container
        @JvmField
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:16.8-alpine").apply {
            withDatabaseName("portfolio")
            withUsername("postgres")
            withPassword("tc_superuser_pw")
        }

        @Container
        @JvmField
        val fakeGcs: FakeGcsTestContainer = FakeGcsTestContainer().apply {
            withExposedPorts(FAKE_GCS_PORT)
            withCommand("-scheme", "http", "-port", FAKE_GCS_PORT.toString())
            waitingFor(Wait.forHttp("/storage/v1/b").forPort(FAKE_GCS_PORT))
        }

        @JvmStatic
        @BeforeAll
        fun bootstrapDatabaseAndBucket() = runTest {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { st ->
                    st.execute(
                        """
                        DO $$
                        BEGIN
                            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$MIGRATOR_ROLE') THEN
                                CREATE ROLE $MIGRATOR_ROLE WITH LOGIN PASSWORD '$ROLE_PASSWORD' CREATEROLE;
                            END IF;
                            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ADMIN_ROLE') THEN
                                CREATE ROLE $ADMIN_ROLE WITH LOGIN PASSWORD '$ROLE_PASSWORD';
                            END IF;
                            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$PUBLIC_ROLE') THEN
                                CREATE ROLE $PUBLIC_ROLE WITH LOGIN PASSWORD '$ROLE_PASSWORD';
                            END IF;
                        END $$;
                        GRANT CONNECT, CREATE ON DATABASE portfolio TO $MIGRATOR_ROLE;
                        GRANT CONNECT ON DATABASE portfolio TO $ADMIN_ROLE, $PUBLIC_ROLE;
                        """.trimIndent(),
                    )
                }
            }

            FlywayMigrator.executeMigrate(
                DatabaseConfig(
                    jdbcUrl = postgres.jdbcUrl,
                    username = MIGRATOR_ROLE,
                    password = ROLE_PASSWORD,
                ),
            )

            val adminFactory = DatabaseFactory.connect(
                DatabaseConfig(
                    jdbcUrl = postgres.jdbcUrl,
                    username = ADMIN_ROLE,
                    password = ROLE_PASSWORD,
                ),
            )
            adminFactory.use { factory ->
                ExposedWriteupRepository(factory).createWriteup(
                    UpsertWriteupRequest(
                        slug = "tc-postgres-ktor-writeup",
                        title = "Least Privilege Postgres Roles in Ktor",
                        summary = "Verified with Testcontainers",
                        bodyMarkdown = "# Postgres Integration\nKtor & Postgres isolation test.",
                        tags = listOf("ktor", "postgres"),
                        status = WriteupStatus.PUBLISHED,
                        readingTimeMin = 4,
                    ),
                )
                ExposedDocumentRepository(factory).saveConfirmedDocument(
                    id = Uuid.random(),
                    sizeBytes = 64L,
                    mimeType = MimeMagicBytes.MIME_PDF,
                    request = ConfirmUploadRequest(
                        documentId = Uuid.random().toString(),
                        storageKey = "documents/seed/tc.pdf",
                        title = "TC Seed Doc",
                        category = DocumentCategory.RESUME,
                        originalFilename = "tc.pdf",
                        declaredMimeType = MimeMagicBytes.MIME_PDF,
                        sha256 = "b".repeat(64),
                        isPublic = true,
                    ),
                )
            }

            val gcsEndpoint = "http://${fakeGcs.host}:${fakeGcs.getMappedPort(FAKE_GCS_PORT)}"
            StorageOptions.newBuilder()
                .setHost(gcsEndpoint)
                .setProjectId(GcsBlobStorageConfig.DEFAULT_LOCAL_PROJECT_ID)
                .setCredentials(NoCredentials.getInstance())
                .build()
                .service
                .use { storage ->
                    storage.create(BucketInfo.of(TEST_BUCKET))
                }
        }
    }
}
