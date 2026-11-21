package codes.cy.portfolio

import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.navigation.DefaultRootComponent
import codes.cy.portfolio.presentation.navigation.PresentationDependencies
import codes.cy.portfolio.ui.PortfolioApp
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

private const val ROUTING_SCHEMA_VERSION: String = "1"
private const val CONTAINER_ID: String = "portfolio-root"
private const val META_API_BASE_URL_SELECTOR: String = "meta[name='portfolio-api-base-url']"

@OptIn(ExperimentalComposeUiApi::class, ExperimentalDecomposeApi::class)
fun main() {
    val apiBaseUrl = resolveApiBaseUrl()
    val dependencies = PresentationDependencies.fromBaseUrl(
        baseUrl = apiBaseUrl,
        platform = ClientPlatform.WEB,
        viewportTierProvider = { ViewportTier.LARGE },
    )
    val platformActions = WebPlatformActions()
    var activeLifecycle: LifecycleRegistry? = null

    val rootValue = withWebHistory(schemaVersion = ROUTING_SCHEMA_VERSION) { stateKeeper, deepLink ->
        activeLifecycle?.destroy()
        val lifecycle = LifecycleRegistry()
        activeLifecycle = lifecycle
        lifecycle.attachToDocument()

        DefaultRootComponent.fromDeepLinkPath(
            componentContext = DefaultComponentContext(
                lifecycle = lifecycle,
                stateKeeper = stateKeeper,
            ),
            dependencies = dependencies,
            initialPath = deepLink ?: window.location.pathname,
            initialViewportTier = ViewportTier.LARGE,
        )
    }

    ComposeViewport(viewportContainerId = CONTAINER_ID) {
        val root by rootValue.subscribeAsState()
        PortfolioApp(
            rootComponent = root,
            platformActions = platformActions,
        )
    }
}

private fun resolveApiBaseUrl(): String {
    val metaElement = document.querySelector(META_API_BASE_URL_SELECTOR) as? HTMLMetaElement
    val configured = metaElement?.content?.trim().orEmpty()
    return configured.ifEmpty { window.location.origin }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun isDocumentHidden(): Boolean = js("document.hidden === true")

private fun LifecycleRegistry.attachToDocument() {
    if (isDocumentHidden()) stop() else resume()
    document.addEventListener("visibilitychange") {
        if (isDocumentHidden()) stop() else resume()
    }
}

private class WebPlatformActions : PlatformActions {
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
