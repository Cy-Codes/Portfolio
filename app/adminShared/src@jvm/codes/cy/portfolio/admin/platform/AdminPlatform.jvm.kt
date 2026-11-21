package codes.cy.portfolio.admin.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val ALLOWED_EXTENSIONS: Set<String> = setOf("pdf", "png", "jpg", "jpeg", "webp", "md", "markdown", "txt")

actual fun createAdminPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(CIO, block)

actual suspend fun pickUploadFile(): SelectedUploadFile? {
    val selectedPath = suspendCancellableCoroutine<File?> { continuation ->
        SwingUtilities.invokeLater {
            val dialog = FileDialog(null as Frame?, "Select Document or Asset to Upload", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                val ext = name.substringAfterLast('.', "").lowercase()
                ext in ALLOWED_EXTENSIONS
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            dialog.dispose()
            if (continuation.isActive) {
                continuation.resume(if (dir != null && file != null) File(dir, file) else null)
            }
        }
    } ?: return null

    return withContext(Dispatchers.IO) {
        val rawBytes = selectedPath.readBytes()
        SelectedUploadFile(
            filename = selectedPath.name,
            mimeType = AllowedUploadMimes.inferFromFilename(selectedPath.name),
            bytes = rawBytes,
        )
    }
}
