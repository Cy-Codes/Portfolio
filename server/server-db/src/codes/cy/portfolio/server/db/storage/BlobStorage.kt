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

data class BlobStorageConfig(
    val endpointUrl: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val forcePathStyle: Boolean,
) {
    companion object {
        const val DEFAULT_ENDPOINT: String = "http://127.0.0.1:9000"
        const val DEFAULT_REGION: String = "us-east-1"
        const val DEFAULT_BUCKET: String = "portfolio-assets"
        const val DEFAULT_ACCESS_KEY: String = "minioadmin"
        const val DEFAULT_SECRET_KEY: String = "dev_minio_change_me"

        fun fromEnv(env: (String) -> String? = System::getenv): BlobStorageConfig =
            BlobStorageConfig(
                endpointUrl = env("BLOB_STORAGE_ENDPOINT")?.takeIf { it.isNotBlank() } ?: DEFAULT_ENDPOINT,
                region = env("BLOB_STORAGE_REGION")?.takeIf { it.isNotBlank() } ?: DEFAULT_REGION,
                bucket = env("BLOB_STORAGE_BUCKET")?.takeIf { it.isNotBlank() } ?: DEFAULT_BUCKET,
                accessKeyId = env("BLOB_STORAGE_ACCESS_KEY")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCESS_KEY,
                secretAccessKey = env("BLOB_STORAGE_SECRET_KEY")?.takeIf { it.isNotBlank() } ?: DEFAULT_SECRET_KEY,
                forcePathStyle = env("BLOB_STORAGE_FORCE_PATH_STYLE")?.toBooleanStrictOrNull() ?: true,
            )
    }
}

interface BlobStorage : AutoCloseable {
    suspend fun presignPut(
        storageKey: String,
        contentType: String,
        ttl: Duration = DEFAULT_PRESIGN_TTL,
    ): PresignedPutObject

    suspend fun verifyObject(storageKey: String): StoredObjectHead?

    suspend fun presignGet(
        storageKey: String,
        downloadFilename: String,
        contentType: String,
        ttl: Duration = DEFAULT_PRESIGN_TTL,
    ): String

    suspend fun deleteObject(storageKey: String)

    companion object {
        val DEFAULT_PRESIGN_TTL: Duration = 15.minutes
    }
}
