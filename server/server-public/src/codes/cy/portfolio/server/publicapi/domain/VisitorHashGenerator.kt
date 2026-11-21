package codes.cy.portfolio.server.publicapi.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Pure daily-salted SHA-256 visitor fingerprint generator storing zero raw IPs or cookies.
 */
object VisitorHashGenerator {
    private const val HASH_ALGORITHM: String = "SHA-256"

    fun computeVisitorHash(
        dateUtc: LocalDate,
        salt: String,
        clientIp: String,
        userAgent: String,
    ): String {
        val normalizedInput = "$dateUtc|${salt.trim()}|${clientIp.trim()}|${userAgent.trim()}"
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            .digest(normalizedInput.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
