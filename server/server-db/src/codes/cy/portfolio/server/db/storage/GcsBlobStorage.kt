package codes.cy.portfolio.server.db.storage

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Instant

data class GcsBlobStorageConfig(
    val bucket: String,
    val emulatorHost: String? = null,
    val publicEmulatorHost: String? = emulatorHost,
    val projectId: String? = null,
) {
    val isEmulator: Boolean
        get() = !emulatorHost.isNullOrBlank()

    companion object {
        const val DEFAULT_BUCKET: String = "portfolio-assets"
        const val DEFAULT_LOCAL_PROJECT_ID: String = "portfolio-local"

        fun fromEnv(env: (String) -> String? = System::getenv): GcsBlobStorageConfig {
            val bucket = env("GCS_BUCKET_NAME")?.takeIf { it.isNotBlank() } ?: DEFAULT_BUCKET
            val emulatorHost = env("STORAGE_EMULATOR_HOST")?.takeIf { it.isNotBlank() }?.trimEnd('/')
            val publicEmulatorHost = env("STORAGE_EMULATOR_PUBLIC_HOST")?.takeIf { it.isNotBlank() }?.trimEnd('/')
                ?: emulatorHost
            val projectId = env("GCS_PROJECT_ID")?.takeIf { it.isNotBlank() }
                ?: if (emulatorHost != null) DEFAULT_LOCAL_PROJECT_ID else null
            return GcsBlobStorageConfig(
                bucket = bucket,
                emulatorHost = emulatorHost,
                publicEmulatorHost = publicEmulatorHost,
                projectId = projectId,
            )
        }
    }
}

/**
 * Native Google Cloud Storage [BlobStorage] implementation.
 * - Production (Cloud Run): keyless Application Default Credentials (ADC) + GCS V4 signed URLs.
 * - Local Docker / Testcontainers: `fsouza/fake-gcs-server` via `STORAGE_EMULATOR_HOST` with
 *   `NoCredentials` and direct XML/JSON emulator upload/download URLs.
 */
class GcsBlobStorage(
    private val config: GcsBlobStorageConfig,
    private val clock: Clock = Clock.systemUTC(),
    private val storage: Storage = buildDefaultStorage(config),
) : BlobStorage {

    override suspend fun presignPut(
        storageKey: String,
        contentType: String,
        ttl: Duration,
    ): PresignedPutObject = withContext(Dispatchers.IO) {
        val nowJava = clock.instant()
        val expiresAt = Instant.fromEpochSeconds(
            nowJava.epochSecond + ttl.inWholeSeconds,
            nowJava.nano.toLong(),
        )
        val url = if (config.isEmulator) {
            initiateEmulatorResumablePut(storageKey = storageKey, contentType = contentType)
        } else {
            val blobInfo = BlobInfo.newBuilder(BlobId.of(config.bucket, storageKey))
                .setContentType(contentType)
                .build()
            storage.signUrl(
                blobInfo,
                ttl.inWholeSeconds,
                TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withContentType(),
            ).toString()
        }
        PresignedPutObject(
            url = url,
            requiredHeaders = mapOf(HEADER_CONTENT_TYPE to contentType),
            expiresAt = expiresAt,
        )
    }

    override suspend fun verifyObject(storageKey: String): StoredObjectHead? = withContext(Dispatchers.IO) {
        val blob = storage.get(BlobId.of(config.bucket, storageKey)) ?: return@withContext null
        val buffer = ByteBuffer.allocate(MimeMagicBytes.HEADER_INSPECTION_BYTES)
        blob.reader().use { channel ->
            // Permitted I/O boundary read loop to fill up to HEADER_INSPECTION_BYTES from GCS ReadChannel.
            while (buffer.hasRemaining()) {
                val bytesRead = channel.read(buffer)
                if (bytesRead <= 0) break
            }
        }
        buffer.flip()
        val leadingBytes = ByteArray(buffer.remaining())
        buffer.get(leadingBytes)
        StoredObjectHead(
            sizeBytes = blob.size,
            contentType = blob.contentType,
            leadingBytes = leadingBytes,
        )
    }

    override suspend fun presignGet(
        storageKey: String,
        downloadFilename: String,
        contentType: String,
        ttl: Duration,
    ): String = withContext(Dispatchers.IO) {
        if (config.isEmulator) {
            val base = requireNotNull(config.publicEmulatorHost)
            val encodedKey = URLEncoder.encode(storageKey, StandardCharsets.UTF_8)
            "$base/storage/v1/b/${encodeSegment(config.bucket)}/o/$encodedKey?alt=media"
        } else {
            val sanitizedFilename = downloadFilename.replace("\"", "_")
            val blobInfo = BlobInfo.newBuilder(BlobId.of(config.bucket, storageKey)).build()
            val queryParams = mapOf(
                QUERY_RESPONSE_CONTENT_TYPE to contentType,
                QUERY_RESPONSE_CONTENT_DISPOSITION to "inline; filename=\"$sanitizedFilename\"",
            )
            storage.signUrl(
                blobInfo,
                ttl.inWholeSeconds,
                TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withQueryParams(queryParams),
            ).toString()
        }
    }

    override suspend fun deleteObject(storageKey: String) {
        withContext(Dispatchers.IO) {
            storage.delete(BlobId.of(config.bucket, storageKey))
        }
    }

    private fun initiateEmulatorResumablePut(
        storageKey: String,
        contentType: String,
    ): String {
        val internalBase = requireNotNull(config.emulatorHost)
        val publicBase = requireNotNull(config.publicEmulatorHost)
        val encodedKey = URLEncoder.encode(storageKey, StandardCharsets.UTF_8)
        val initUri = URI.create(
            "$internalBase/upload/storage/v1/b/${encodeSegment(config.bucket)}/o?uploadType=resumable&name=$encodedKey",
        )
        val request = HttpRequest.newBuilder(initUri)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header(HEADER_CONTENT_TYPE, "application/json")
            .header(HEADER_UPLOAD_CONTENT_TYPE, contentType)
            .build()
        val response = emulatorHttpClient.send(request, HttpResponse.BodyHandlers.discarding())
        check(response.statusCode() in HTTP_SUCCESS_RANGE) {
            "Failed to initiate GCS emulator resumable upload (status=${response.statusCode()})."
        }
        val location = response.headers().firstValue(HEADER_LOCATION).orElseThrow {
            IllegalStateException("GCS emulator resumable upload response omitted Location header.")
        }
        val parsed = URI.create(location)
        val pathAndQuery = buildString {
            append(parsed.rawPath)
            if (!parsed.rawQuery.isNullOrBlank()) {
                append('?')
                append(parsed.rawQuery)
            }
        }
        return "$publicBase$pathAndQuery"
    }

    override fun close() {
        runCatching { storage.close() }
    }

    companion object {
        private const val HEADER_CONTENT_TYPE: String = "Content-Type"
        private const val HEADER_UPLOAD_CONTENT_TYPE: String = "X-Upload-Content-Type"
        private const val HEADER_LOCATION: String = "Location"
        private const val QUERY_RESPONSE_CONTENT_TYPE: String = "response-content-type"
        private const val QUERY_RESPONSE_CONTENT_DISPOSITION: String = "response-content-disposition"
        private val HTTP_SUCCESS_RANGE: IntRange = 200..299
        private val emulatorHttpClient: HttpClient = HttpClient.newHttpClient()

        private fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")

        private fun buildDefaultStorage(config: GcsBlobStorageConfig): Storage {
            val builder = StorageOptions.newBuilder()
            config.projectId?.let { builder.setProjectId(it) }
            config.emulatorHost?.let { host ->
                builder.setHost(host)
                builder.setCredentials(NoCredentials.getInstance())
            }
            return builder.build().service
        }
    }
}
