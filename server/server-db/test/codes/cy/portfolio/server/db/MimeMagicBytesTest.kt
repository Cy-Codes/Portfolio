package codes.cy.portfolio.server.db.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MimeMagicBytesTest {
    @Test
    fun `accepts valid PDF and PNG magic bytes matching declared MIME`() {
        val pdfBytes = "%PDF-1.7\n...".encodeToByteArray()
        val pdfResult = MimeMagicBytes.detect(pdfBytes, MimeMagicBytes.MIME_PDF)
        assertTrue(pdfResult.isAllowed)
        assertEquals(MimeMagicBytes.MIME_PDF, pdfResult.mimeType)

        val pngBytes = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
        )
        val pngResult = MimeMagicBytes.detect(pngBytes, MimeMagicBytes.MIME_PNG)
        assertTrue(pngResult.isAllowed)
        assertEquals(MimeMagicBytes.MIME_PNG, pngResult.mimeType)
    }

    @Test
    fun `rejects spoofed extension or disallowed executable headers`() {
        val elfHeader = byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x00, 0x01)
        val spoofedPdf = MimeMagicBytes.detect(elfHeader, MimeMagicBytes.MIME_PDF)
        assertFalse(spoofedPdf.isAllowed)

        val pdfBytes = "%PDF-1.7\n...".encodeToByteArray()
        val mismatched = MimeMagicBytes.detect(pdfBytes, MimeMagicBytes.MIME_PNG)
        assertFalse(mismatched.isAllowed)
    }

    @Test
    fun `validates upload byte size bounds`() {
        assertFalse(MimeMagicBytes.isAllowedUploadSize(0L))
        assertFalse(MimeMagicBytes.isAllowedUploadSize(-1L))
        assertTrue(MimeMagicBytes.isAllowedUploadSize(1024L))
        assertTrue(MimeMagicBytes.isAllowedUploadSize(MimeMagicBytes.MAX_UPLOAD_BYTES))
        assertFalse(MimeMagicBytes.isAllowedUploadSize(MimeMagicBytes.MAX_UPLOAD_BYTES + 1L))
    }
}
