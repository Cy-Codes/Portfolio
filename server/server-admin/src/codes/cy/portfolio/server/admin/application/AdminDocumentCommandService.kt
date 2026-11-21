package codes.cy.portfolio.server.admin.application

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.PresignUploadRequest
import codes.cy.portfolio.core.model.PresignUploadResponse
import codes.cy.portfolio.server.admin.domain.AdminValidation
import codes.cy.portfolio.server.db.errors.NotFoundException
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.repositories.DocumentCommandRepository
import codes.cy.portfolio.server.db.storage.BlobStorage
import codes.cy.portfolio.server.db.storage.MimeMagicBytes
import kotlin.uuid.Uuid

/**
 * Coordinates direct-to-R2 presigned PUT issuance, magic-byte MIME verification on confirm,
 * and coordinated object + metadata deletion.
 */
class AdminDocumentCommandService(
    private val documentRepo: DocumentCommandRepository,
    private val blobStorage: BlobStorage,
) {
    suspend fun presignUpload(request: PresignUploadRequest): PresignUploadResponse {
        AdminValidation.validatePresignRequest(request)
        val documentUuid = Uuid.random()
        val safeFilename = AdminValidation.sanitizeFilenameForKey(request.originalFilename)
        val storageKey = "documents/$documentUuid/$safeFilename"
        val normalizedMime = request.mimeType.trim().lowercase()
        val presigned = blobStorage.presignPut(
            storageKey = storageKey,
            contentType = normalizedMime,
        )
        return PresignUploadResponse(
            documentId = documentUuid.toString(),
            storageKey = storageKey,
            presignedPutUrl = presigned.url,
            requiredHeaders = presigned.requiredHeaders,
            expiresAt = presigned.expiresAt,
        )
    }

    suspend fun confirmUpload(request: ConfirmUploadRequest): DocumentDto {
        val documentUuid = AdminValidation.validateConfirmRequest(request)
        val storedHead = blobStorage.verifyObject(request.storageKey)
            ?: throw ValidationException(
                message = "Uploaded object '${request.storageKey}' does not exist in storage.",
                fieldErrors = listOf(
                    FieldViolation("storageKey", "Upload binary to presigned URL before confirming."),
                ),
            )

        if (!MimeMagicBytes.isAllowedUploadSize(storedHead.sizeBytes)) {
            runCatching { blobStorage.deleteObject(request.storageKey) }
            throw ValidationException(
                message = "Uploaded object size (${storedHead.sizeBytes} bytes) is outside allowed limits.",
                fieldErrors = listOf(FieldViolation("sizeBytes", "Size must be 1..${MimeMagicBytes.MAX_UPLOAD_BYTES}.")),
            )
        }

        val detected = MimeMagicBytes.detect(storedHead.leadingBytes, request.declaredMimeType)
        if (!detected.isAllowed) {
            runCatching { blobStorage.deleteObject(request.storageKey) }
            throw ValidationException(
                message = "Uploaded file content (${detected.mimeType}) does not match declared MIME '${request.declaredMimeType}'.",
                fieldErrors = listOf(FieldViolation("declaredMimeType", "Magic-byte inspection failed.")),
            )
        }

        return documentRepo.saveConfirmedDocument(
            id = documentUuid,
            sizeBytes = storedHead.sizeBytes,
            mimeType = detected.mimeType,
            request = request,
        )
    }

    suspend fun deleteDocument(rawId: String) {
        val documentUuid = AdminValidation.parseUuidOrThrow(rawId, "id")
        val deleted = documentRepo.deleteDocument(documentUuid)
            ?: throw NotFoundException(resource = "document", identifier = rawId)
        blobStorage.deleteObject(deleted.storageKey)
    }
}
