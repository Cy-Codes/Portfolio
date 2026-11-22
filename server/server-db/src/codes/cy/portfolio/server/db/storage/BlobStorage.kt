package codes.cy.portfolio.server.db.storage

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class PresignedPutObject(
    val url: String,
    val requiredHeaders: Map<String, String>,
    val expiresAt: Instant,
)

data class StoredObjectHead(
    val sizeBytes: Long,
    val contentType: String?,
    val leadingBytes: ByteArray,
)

/**
 * Least-privilege read-only object storage capability exposed to public read services.
 */
interface BlobStorageReader : AutoCloseable {
    suspend fun presignGet(
        storageKey: String,
        downloadFilename: String,
        contentType: String,
        ttl: Duration = DEFAULT_PRESIGN_TTL,
    ): String

    companion object {
        val DEFAULT_PRESIGN_TTL: Duration = 15.minutes
    }
}

/**
 * Full read-write object storage capability restricted to admin CMS services.
 */
interface BlobStorage : BlobStorageReader {
    suspend fun presignPut(
        storageKey: String,
        contentType: String,
        ttl: Duration = BlobStorageReader.DEFAULT_PRESIGN_TTL,
    ): PresignedPutObject

    suspend fun verifyObject(storageKey: String): StoredObjectHead?

    suspend fun deleteObject(storageKey: String)
}
