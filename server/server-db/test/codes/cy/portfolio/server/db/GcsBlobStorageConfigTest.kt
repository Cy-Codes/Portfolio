package codes.cy.portfolio.server.db

import codes.cy.portfolio.server.db.storage.GcsBlobStorageConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GcsBlobStorageConfigTest {

    @Test
    fun `GcsBlobStorageConfig resolves defaults for production ADC and local emulator`() {
        val prodConfig = GcsBlobStorageConfig.fromEnv { key ->
            when (key) {
                "GCS_BUCKET_NAME" -> "my-proj-portfolio-prod-assets"
                else -> null
            }
        }
        assertEquals("my-proj-portfolio-prod-assets", prodConfig.bucket)
        assertNull(prodConfig.emulatorHost)
        assertNull(prodConfig.publicEmulatorHost)
        assertNull(prodConfig.projectId)
        assertFalse(prodConfig.isEmulator)

        val localConfig = GcsBlobStorageConfig.fromEnv { key ->
            when (key) {
                "GCS_BUCKET_NAME" -> "portfolio-assets"
                "STORAGE_EMULATOR_HOST" -> "http://fake-gcs:4443/"
                "STORAGE_EMULATOR_PUBLIC_HOST" -> "http://127.0.0.1:4443/"
                else -> null
            }
        }
        assertEquals("portfolio-assets", localConfig.bucket)
        assertEquals("http://fake-gcs:4443", localConfig.emulatorHost)
        assertEquals("http://127.0.0.1:4443", localConfig.publicEmulatorHost)
        assertEquals(GcsBlobStorageConfig.DEFAULT_LOCAL_PROJECT_ID, localConfig.projectId)
        assertTrue(localConfig.isEmulator)
    }
}
