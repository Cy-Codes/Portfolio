package codes.cy.portfolio.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class DocumentDto(
    val id: String,
    val title: String,
    val category: DocumentCategory,
    val originalFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val storageKey: String,
    val isPublic: Boolean,
    val downloadCount: Long = 0L,
    val createdAt: Instant,
)

@Serializable
data class PresignUploadRequest(
    val originalFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class PresignUploadResponse(
    val documentId: String,
    val storageKey: String,
    val presignedPutUrl: String,
    val requiredHeaders: Map<String, String> = emptyMap(),
    val expiresAt: Instant,
)

@Serializable
data class ConfirmUploadRequest(
    val documentId: String,
    val storageKey: String,
    val title: String,
    val category: DocumentCategory,
    val originalFilename: String,
    val declaredMimeType: String,
    val sha256: String,
    val isPublic: Boolean = true,
)
