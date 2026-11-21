package codes.cy.portfolio.server.db.storage

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.net.url.Url
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * S3-compatible [BlobStorage] implementation supporting Cloudflare R2 (`region = "auto"`)
 * and local MinIO (`forcePathStyle = true`), including separate internal vs. host-reachable
 * presigning endpoints for Dockerized deployments.
 */
class S3BlobStorage(
    private val config: BlobStorageConfig,
    private val clock: Clock = Clock.systemUTC(),
) : BlobStorage {

    private val client: S3Client = buildClient(config.endpointUrl)
    private val presignClient: S3Client =
        if (config.publicEndpointUrl == config.endpointUrl) client else buildClient(config.publicEndpointUrl)

    private fun buildClient(targetEndpointUrl: String): S3Client =
        S3Client {
            region = config.region
            endpointUrl = Url.parse(targetEndpointUrl)
            forcePathStyle = config.forcePathStyle
            credentialsProvider = StaticCredentialsProvider(
                Credentials(
                    accessKeyId = config.accessKeyId,
                    secretAccessKey = config.secretAccessKey,
                ),
            )
        }

    override suspend fun presignPut(
        storageKey: String,
        contentType: String,
        ttl: Duration,
    ): PresignedPutObject {
        val putRequest = PutObjectRequest {
            bucket = config.bucket
            key = storageKey
            this.contentType = contentType
        }
        val presigned = presignClient.presignPutObject(putRequest, ttl)
        val headers = presigned.headers.entries()
            .associate { (name, values) -> name to values.joinToString(",") }
        val nowJava = clock.instant()
        val expiresAt = Instant.fromEpochSeconds(
            nowJava.epochSecond + ttl.inWholeSeconds,
            nowJava.nano.toLong(),
        )
        return PresignedPutObject(
            url = presigned.url.toString(),
            requiredHeaders = headers,
            expiresAt = expiresAt,
        )
    }

    override suspend fun verifyObject(storageKey: String): StoredObjectHead? =
        try {
            val head = client.headObject(
                HeadObjectRequest {
                    bucket = config.bucket
                    key = storageKey
                },
            )
            val rangeEnd = MimeMagicBytes.HEADER_INSPECTION_BYTES - 1
            val leadingBytes = client.getObject(
                GetObjectRequest {
                    bucket = config.bucket
                    key = storageKey
                    range = "bytes=0-$rangeEnd"
                },
            ) { response ->
                response.body?.toByteArray() ?: ByteArray(0)
            }
            StoredObjectHead(
                sizeBytes = head.contentLength ?: 0L,
                contentType = head.contentType,
                leadingBytes = leadingBytes,
            )
        } catch (_: NoSuchKey) {
            null
        } catch (ex: S3Exception) {
            if (ex.sdkErrorMetadata.errorCode == NOT_FOUND_ERROR_CODE) null else throw ex
        }

    override suspend fun presignGet(
        storageKey: String,
        downloadFilename: String,
        contentType: String,
        ttl: Duration,
    ): String {
        val sanitizedFilename = downloadFilename.replace("\"", "_")
        val getRequest = GetObjectRequest {
            bucket = config.bucket
            key = storageKey
            responseContentType = contentType
            responseContentDisposition = "inline; filename=\"$sanitizedFilename\""
        }
        return presignClient.presignGetObject(getRequest, ttl).url.toString()
    }

    override suspend fun deleteObject(storageKey: String) {
        client.deleteObject(
            DeleteObjectRequest {
                bucket = config.bucket
                key = storageKey
            },
        )
    }

    override fun close() {
        if (presignClient !== client) {
            presignClient.close()
        }
        client.close()
    }

    companion object {
        private const val NOT_FOUND_ERROR_CODE: String = "NotFound"
    }
}
