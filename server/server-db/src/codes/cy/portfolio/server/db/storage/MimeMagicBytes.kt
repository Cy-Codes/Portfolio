package codes.cy.portfolio.server.db.storage

data class DetectedMime(
    val mimeType: String,
    val isAllowed: Boolean,
)

/**
 * Pure magic-byte content sniffer enforcing upload MIME integrity independent of filename.
 */
object MimeMagicBytes {
    const val MAX_UPLOAD_BYTES: Long = 25L * 1024L * 1024L
    const val HEADER_INSPECTION_BYTES: Int = 64

    const val MIME_PDF: String = "application/pdf"
    const val MIME_PNG: String = "image/png"
    const val MIME_JPEG: String = "image/jpeg"
    const val MIME_WEBP: String = "image/webp"
    const val MIME_MARKDOWN: String = "text/markdown"
    const val MIME_PLAIN_TEXT: String = "text/plain"

    val ALLOWED_MIME_TYPES: Set<String> = setOf(
        MIME_PDF,
        MIME_PNG,
        MIME_JPEG,
        MIME_WEBP,
        MIME_MARKDOWN,
        MIME_PLAIN_TEXT,
    )

    private val PDF_MAGIC: ByteArray = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-
    private val PNG_MAGIC: ByteArray = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    private val JPEG_MAGIC: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val RIFF_MAGIC: ByteArray = byteArrayOf(0x52, 0x49, 0x46, 0x46)
    private val WEBP_MAGIC: ByteArray = byteArrayOf(0x57, 0x45, 0x42, 0x50)
    private const val WEBP_RIFF_HEADER_LENGTH: Int = 12
    private const val WEBP_SUBTYPE_OFFSET: Int = 8
    private const val NUL_BYTE: Byte = 0

    fun isAllowedDeclaredMime(mimeType: String): Boolean =
        mimeType.trim().lowercase() in ALLOWED_MIME_TYPES

    fun isAllowedUploadSize(sizeBytes: Long): Boolean =
        sizeBytes in 1L..MAX_UPLOAD_BYTES

    fun detect(headerBytes: ByteArray, declaredMimeType: String): DetectedMime {
        val normalizedDeclared = declaredMimeType.trim().lowercase()
        val detected = when {
            headerBytes.startsWith(PDF_MAGIC) -> MIME_PDF
            headerBytes.startsWith(PNG_MAGIC) -> MIME_PNG
            headerBytes.startsWith(JPEG_MAGIC) -> MIME_JPEG
            headerBytes.isWebp() -> MIME_WEBP
            isLikelyUtf8Text(headerBytes) && normalizedDeclared == MIME_MARKDOWN -> MIME_MARKDOWN
            isLikelyUtf8Text(headerBytes) && normalizedDeclared == MIME_PLAIN_TEXT -> MIME_PLAIN_TEXT
            else -> return DetectedMime(mimeType = "application/octet-stream", isAllowed = false)
        }
        return DetectedMime(
            mimeType = detected,
            isAllowed = detected in ALLOWED_MIME_TYPES && detected == normalizedDeclared,
        )
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { idx -> this[idx] == prefix[idx] }

    private fun ByteArray.isWebp(): Boolean =
        size >= WEBP_RIFF_HEADER_LENGTH &&
            startsWith(RIFF_MAGIC) &&
            WEBP_MAGIC.indices.all { idx -> this[WEBP_SUBTYPE_OFFSET + idx] == WEBP_MAGIC[idx] }

    private fun isLikelyUtf8Text(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.none { it == NUL_BYTE }
}
