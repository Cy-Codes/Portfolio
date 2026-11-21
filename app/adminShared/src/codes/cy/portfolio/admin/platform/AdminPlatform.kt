package codes.cy.portfolio.admin.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

class SelectedUploadFile(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectedUploadFile) return false
        return filename == other.filename &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String =
        "SelectedUploadFile(filename='$filename', mimeType='$mimeType', sizeBytes=${bytes.size})"
}

object AllowedUploadMimes {
    const val PDF: String = "application/pdf"
    const val PNG: String = "image/png"
    const val JPEG: String = "image/jpeg"
    const val WEBP: String = "image/webp"
    const val MARKDOWN: String = "text/markdown"
    const val PLAIN_TEXT: String = "text/plain"
    const val OCTET_STREAM: String = "application/octet-stream"

    val ALL: Set<String> = setOf(PDF, PNG, JPEG, WEBP, MARKDOWN, PLAIN_TEXT)

    fun inferFromFilename(
        filename: String,
        reportedMimeType: String? = null,
    ): String {
        val ext = filename.trim().substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val fromExt = when (ext) {
            "pdf" -> PDF
            "png" -> PNG
            "jpg", "jpeg" -> JPEG
            "webp" -> WEBP
            "md", "markdown" -> MARKDOWN
            "txt" -> PLAIN_TEXT
            else -> null
        }
        if (fromExt != null) return fromExt
        val normalizedReported = reportedMimeType?.trim()?.lowercase()
        return if (normalizedReported != null && normalizedReported in ALL) normalizedReported else OCTET_STREAM
    }
}

expect fun createAdminPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

expect suspend fun pickUploadFile(): SelectedUploadFile?
