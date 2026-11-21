package codes.cy.portfolio.admin

import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import codes.cy.portfolio.admin.navigation.AdminDependencies
import codes.cy.portfolio.admin.navigation.DefaultAdminRootComponent
import codes.cy.portfolio.ui.platform.PlatformActions
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.webhistory.withWebHistory
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLMetaElement

private const val ROUTING_SCHEMA_VERSION: String = "admin-1"
private const val CONTAINER_ID: String = "admin-root"
private const val META_ADMIN_API_BASE_URL_SELECTOR: String = "meta[name='portfolio-admin-api-base-url']"
private const val LOCAL_ADMIN_BASE_URL: String = "http://127.0.0.1:8081"

@OptIn(ExperimentalComposeUiApi::class, ExperimentalDecomposeApi::class)
fun main() {
    val apiBaseUrl = resolveAdminWebApiBaseUrl()
    val dependencies = AdminDependencies.fromBaseUrl(baseUrl = apiBaseUrl)
    val platformActions = WebAdminPlatformActions()
    var activeLifecycle: LifecycleRegistry? = null

    val rootValue = withWebHistory(schemaVersion = ROUTING_SCHEMA_VERSION) { stateKeeper, deepLink ->
        activeLifecycle?.destroy()
        val lifecycle = LifecycleRegistry()
        activeLifecycle = lifecycle
        lifecycle.attachToDocument()

        DefaultAdminRootComponent.fromDeepLinkPath(
            componentContext = DefaultComponentContext(
                lifecycle = lifecycle,
                stateKeeper = stateKeeper,
            ),
            dependencies = dependencies,
            initialPath = deepLink ?: window.location.pathname,
        )
    }

    ComposeViewport(viewportContainerId = CONTAINER_ID) {
        val root by rootValue.subscribeAsState()
        AdminApp(
            rootComponent = root,
            platformActions = platformActions,
        )
    }
}

private fun resolveAdminWebApiBaseUrl(): String {
    val metaElement = document.querySelector(META_ADMIN_API_BASE_URL_SELECTOR) as? HTMLMetaElement
    val configured = metaElement?.content?.trim().orEmpty()
    if (configured.isNotEmpty()) {
        return configured
    }
    val host = window.location.hostname
    return if (host == "localhost" || host == "127.0.0.1") {
        LOCAL_ADMIN_BASE_URL
    } else {
        window.location.origin
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun isDocumentHidden(): Boolean = js("document.hidden === true")

private fun LifecycleRegistry.attachToDocument() {
    if (isDocumentHidden()) stop() else resume()
    document.addEventListener("visibilitychange") {
        if (isDocumentHidden()) stop() else resume()
    }
}

private class WebAdminPlatformActions : PlatformActions {
    override fun openExternalUrl(url: String) {
        window.open(url, "_blank", "noopener,noreferrer")
    }

    override fun triggerDownload(url: String, filename: String) {
        val body = document.body
        if (body == null) {
            window.open(url, "_blank", "noopener,noreferrer")
            return
        }
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = filename
        anchor.target = "_blank"
        anchor.rel = "noopener noreferrer"
        anchor.style.display = "none"
        body.appendChild(anchor)
        anchor.click()
        body.removeChild(anchor)
    }
}
