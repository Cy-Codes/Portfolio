package codes.cy.portfolio.server.admin.domain

import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.PresignUploadRequest
import codes.cy.portfolio.core.model.UpdateProfileRequest
import codes.cy.portfolio.core.model.UpsertCertificationRequest
import codes.cy.portfolio.core.model.UpsertEducationRequest
import codes.cy.portfolio.core.model.UpsertExperienceRequest
import codes.cy.portfolio.core.model.UpsertProjectRequest
import codes.cy.portfolio.core.model.UpsertSkillRequest
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.validation.isSafeSlug
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.storage.MimeMagicBytes
import kotlin.uuid.Uuid

/**
 * Pure request validation rules throwing typed [ValidationException] with structured field violations.
 */
object AdminValidation {
    private val SHA256_REGEX: Regex = Regex("^[a-fA-F0-9]{64}$")
    private const val STORAGE_KEY_PREFIX: String = "documents/"

    fun parseUuidOrThrow(raw: String, fieldName: String = "id"): Uuid =
        runCatching { Uuid.parse(raw.trim()) }.getOrElse {
            throw ValidationException(
                message = "Invalid UUID for '$fieldName'.",
                fieldErrors = listOf(FieldViolation(field = fieldName, message = "Must be a valid UUID.")),
            )
        }

    fun validateProfileRequest(request: UpdateProfileRequest) {
        val violations = buildList {
            if (request.fullName.isBlank()) add(FieldViolation("fullName", "fullName must not be blank."))
            if (request.headline.isBlank()) add(FieldViolation("headline", "headline must not be blank."))
            if (request.bioMarkdown.isBlank()) add(FieldViolation("bioMarkdown", "bioMarkdown must not be blank."))
            request.resumeDocumentId?.let { id ->
                if (runCatching { Uuid.parse(id.trim()) }.isFailure) {
                    add(FieldViolation("resumeDocumentId", "Must be a valid UUID."))
                }
            }
        }
        throwIfViolations("Invalid profile update payload.", violations)
    }

    fun validateSkillRequest(request: UpsertSkillRequest) {
        val violations = buildList {
            if (request.name.isBlank()) add(FieldViolation("name", "name must not be blank."))
            if (request.category.isBlank()) add(FieldViolation("category", "category must not be blank."))
        }
        throwIfViolations("Invalid skill payload.", violations)
    }

    fun validateExperienceRequest(request: UpsertExperienceRequest) {
        val violations = buildList {
            if (request.company.isBlank()) add(FieldViolation("company", "company must not be blank."))
            if (request.title.isBlank()) add(FieldViolation("title", "title must not be blank."))
            request.skillIds.forEachIndexed { idx, skillId ->
                if (runCatching { Uuid.parse(skillId.trim()) }.isFailure) {
                    add(FieldViolation("skillIds[$idx]", "Must be a valid UUID."))
                }
            }
        }
        throwIfViolations("Invalid experience payload.", violations)
    }

    fun validateEducationRequest(request: UpsertEducationRequest) {
        val violations = buildList {
            if (request.institution.isBlank()) add(FieldViolation("institution", "institution must not be blank."))
            if (request.degree.isBlank()) add(FieldViolation("degree", "degree must not be blank."))
        }
        throwIfViolations("Invalid education payload.", violations)
    }

    fun validateCertificationRequest(request: UpsertCertificationRequest) {
        val violations = buildList {
            if (request.name.isBlank()) add(FieldViolation("name", "name must not be blank."))
            if (request.issuer.isBlank()) add(FieldViolation("issuer", "issuer must not be blank."))
        }
        throwIfViolations("Invalid certification payload.", violations)
    }

    fun validateProjectRequest(request: UpsertProjectRequest) {
        val violations = buildList {
            if (!request.slug.trim().isSafeSlug()) {
                add(FieldViolation("slug", "Slug must be lowercase alphanumeric hyphenated words."))
            }
            if (request.title.isBlank()) add(FieldViolation("title", "title must not be blank."))
            if (request.summary.isBlank()) add(FieldViolation("summary", "summary must not be blank."))
            if (request.caseStudyMarkdown.isBlank()) {
                add(FieldViolation("caseStudyMarkdown", "caseStudyMarkdown must not be blank."))
            }
            request.coverDocumentId?.let { coverId ->
                if (runCatching { Uuid.parse(coverId.trim()) }.isFailure) {
                    add(FieldViolation("coverDocumentId", "Must be a valid UUID."))
                }
            }
            request.skillIds.forEachIndexed { idx, skillId ->
                if (runCatching { Uuid.parse(skillId.trim()) }.isFailure) {
                    add(FieldViolation("skillIds[$idx]", "Must be a valid UUID."))
                }
            }
        }
        throwIfViolations("Invalid project payload.", violations)
    }

    fun validateWriteupRequest(request: UpsertWriteupRequest) {
        val violations = buildList {
            if (!request.slug.trim().isSafeSlug()) {
                add(FieldViolation("slug", "Slug must be lowercase alphanumeric hyphenated words."))
            }
            if (request.title.isBlank()) add(FieldViolation("title", "title must not be blank."))
            if (request.summary.isBlank()) add(FieldViolation("summary", "summary must not be blank."))
            if (request.bodyMarkdown.isBlank()) add(FieldViolation("bodyMarkdown", "bodyMarkdown must not be blank."))
            if (request.readingTimeMin < UpsertWriteupRequest.DEFAULT_READING_TIME_MIN) {
                add(FieldViolation("readingTimeMin", "readingTimeMin must be at least 1."))
            }
            request.coverDocumentId?.let { coverId ->
                if (runCatching { Uuid.parse(coverId.trim()) }.isFailure) {
                    add(FieldViolation("coverDocumentId", "Must be a valid UUID."))
                }
            }
        }
        throwIfViolations("Invalid writeup payload.", violations)
    }

    fun validatePresignRequest(request: PresignUploadRequest) {
        val violations = buildList {
            if (request.originalFilename.isBlank()) {
                add(FieldViolation("originalFilename", "originalFilename must not be blank."))
            }
            if (!MimeMagicBytes.isAllowedDeclaredMime(request.mimeType)) {
                add(
                    FieldViolation(
                        "mimeType",
                        "Unsupported MIME type '${request.mimeType}'. Allowed: ${MimeMagicBytes.ALLOWED_MIME_TYPES.sorted().joinToString(", ")}.",
                    ),
                )
            }
            if (!MimeMagicBytes.isAllowedUploadSize(request.sizeBytes)) {
                add(
                    FieldViolation(
                        "sizeBytes",
                        "Upload size must be between 1 and ${MimeMagicBytes.MAX_UPLOAD_BYTES} bytes.",
                    ),
                )
            }
            if (!SHA256_REGEX.matches(request.sha256.trim())) {
                add(FieldViolation("sha256", "sha256 must be a 64-character hex digest."))
            }
        }
        throwIfViolations("Invalid presign upload request.", violations)
    }

    fun validateConfirmRequest(request: ConfirmUploadRequest): Uuid {
        val parsedDocUuid = runCatching { Uuid.parse(request.documentId.trim()) }.getOrNull()
        val violations = buildList {
            if (parsedDocUuid == null) {
                add(FieldViolation("documentId", "Must be a valid UUID."))
            }
            if (request.title.isBlank()) add(FieldViolation("title", "title must not be blank."))
            if (request.originalFilename.isBlank()) {
                add(FieldViolation("originalFilename", "originalFilename must not be blank."))
            }
            if (!MimeMagicBytes.isAllowedDeclaredMime(request.declaredMimeType)) {
                add(FieldViolation("declaredMimeType", "Unsupported MIME type '${request.declaredMimeType}'."))
            }
            if (!SHA256_REGEX.matches(request.sha256.trim())) {
                add(FieldViolation("sha256", "sha256 must be a 64-character hex digest."))
            }
            if (parsedDocUuid != null && !request.storageKey.startsWith("$STORAGE_KEY_PREFIX$parsedDocUuid/")) {
                add(FieldViolation("storageKey", "storageKey must belong to document '$parsedDocUuid'."))
            }
        }
        throwIfViolations("Invalid confirm upload request.", violations)
        return requireNotNull(parsedDocUuid)
    }

    fun sanitizeFilenameForKey(rawFilename: String): String {
        val baseName = rawFilename.trim().substringAfterLast('/').substringAfterLast('\\')
        val sanitized = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        return sanitized.ifEmpty { "upload.bin" }
    }

    private fun throwIfViolations(message: String, violations: List<FieldViolation>) {
        if (violations.isNotEmpty()) {
            throw ValidationException(message = message, fieldErrors = violations)
        }
    }
}
