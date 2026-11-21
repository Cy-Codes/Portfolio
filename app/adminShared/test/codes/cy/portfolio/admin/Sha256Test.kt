package codes.cy.portfolio.admin

import codes.cy.portfolio.admin.crypto.sha256Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun `sha256Hex matches standard FIPS 180-4 empty and short ASCII test vectors`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ByteArray(0).sha256Hex(),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".encodeToByteArray().sha256Hex(),
        )
    }

    @Test
    fun `sha256Hex matches standard FIPS 180-4 multi-block and binary vectors`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray().sha256Hex(),
        )

        val pdfBytes = "%PDF-1.7\nhello portfolio".encodeToByteArray()
        assertEquals(64, pdfBytes.sha256Hex().length)
        assertEquals(pdfBytes.sha256Hex(), pdfBytes.copyOf().sha256Hex())
    }
}
