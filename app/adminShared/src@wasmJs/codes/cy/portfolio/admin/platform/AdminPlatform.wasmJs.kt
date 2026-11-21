package codes.cy.portfolio.admin.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine

private const val FILE_ACCEPT_EXTENSIONS: String = ".pdf,.png,.jpg,.jpeg,.webp,.md,.markdown,.txt"

actual fun createAdminPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Js, block)

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun pickUploadFile(): SelectedUploadFile? =
    suspendCancellableCoroutine { continuation ->
        openBrowserFileInput(
            accept = FILE_ACCEPT_EXTENSIONS,
            onFileLoaded = { filename, reportedMime, base64Content ->
                if (!continuation.isActive) return@openBrowserFileInput
                val decoded = runCatching { Base64.decode(base64Content) }.getOrNull()
                if (decoded == null) {
                    continuation.resume(null)
                } else {
                    continuation.resume(
                        SelectedUploadFile(
                            filename = filename,
                            mimeType = AllowedUploadMimes.inferFromFilename(filename, reportedMime),
                            bytes = decoded,
                        ),
                    )
                }
            },
            onCancelled = {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            },
        )
    }

@OptIn(ExperimentalWasmJsInterop::class)
private fun openBrowserFileInput(
    accept: String,
    onFileLoaded: (filename: String, mimeType: String, base64Content: String) -> Unit,
    onCancelled: () -> Unit,
): Unit = js(
    """
    (() => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        input.style.display = 'none';
        let settled = false;
        const cleanup = () => {
            if (input.parentNode) {
                input.parentNode.removeChild(input);
            }
        };
        input.addEventListener('cancel', () => {
            if (!settled) {
                settled = true;
                cleanup();
                onCancelled();
            }
        }, { once: true });
        input.addEventListener('change', () => {
            if (settled) return;
            settled = true;
            const file = input.files && input.files[0];
            if (!file) {
                cleanup();
                onCancelled();
                return;
            }
            const reader = new FileReader();
            reader.onload = () => {
                const raw = typeof reader.result === 'string' ? reader.result : '';
                const commaIdx = raw.indexOf(',');
                const base64 = commaIdx >= 0 ? raw.slice(commaIdx + 1) : '';
                cleanup();
                onFileLoaded(file.name || 'upload.bin', file.type || '', base64);
            };
            reader.onerror = () => {
                cleanup();
                onCancelled();
            };
            reader.readAsDataURL(file);
        }, { once: true });
        document.body.appendChild(input);
        input.click();
    })()
    """,
)
