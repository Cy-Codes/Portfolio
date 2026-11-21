package codes.cy.portfolio

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.navigation.DefaultRootComponent
import codes.cy.portfolio.presentation.navigation.PresentationDependencies
import codes.cy.portfolio.presentation.navigation.TopLevelDestination
import codes.cy.portfolio.ui.PortfolioApp
import codes.cy.portfolio.ui.platform.PlatformActions
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import java.awt.Desktop
import java.awt.Dimension
import java.net.URI

private const val DEFAULT_DESKTOP_API_BASE_URL: String = "http://127.0.0.1:8080"
private const val PROPERTY_API_BASE_URL: String = "portfolio.apiBaseUrl"
private const val ENV_API_BASE_URL: String = "PORTFOLIO_API_BASE_URL"
private const val INITIAL_WINDOW_WIDTH_DP: Int = 1280
private const val INITIAL_WINDOW_HEIGHT_DP: Int = 840
private const val MIN_WINDOW_WIDTH_PX: Int = 360
private const val MIN_WINDOW_HEIGHT_PX: Int = 520

fun main() = application {
    val lifecycle = remember { LifecycleRegistry() }
    val rootComponent = remember(lifecycle) {
        val dependencies = PresentationDependencies.fromBaseUrl(
            baseUrl = resolveDesktopApiBaseUrl(),
            platform = ClientPlatform.DESKTOP,
            viewportTierProvider = { ViewportTier.LARGE },
        )
        DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            baseDependencies = dependencies,
            initialViewportTier = ViewportTier.LARGE,
        )
    }
    val platformActions = remember { DesktopPlatformActions() }
    val windowState = rememberWindowState(
        width = INITIAL_WINDOW_WIDTH_DP.dp,
        height = INITIAL_WINDOW_HEIGHT_DP.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "CY · Portfolio",
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                handleDesktopShortcut(event.isCtrlPressed || event.isMetaPressed, event.key, rootComponent)
            }
        },
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(MIN_WINDOW_WIDTH_PX, MIN_WINDOW_HEIGHT_PX)
        }
        LifecycleController(lifecycle, windowState, LocalWindowInfo.current)
        PortfolioApp(
            rootComponent = rootComponent,
            platformActions = platformActions,
        )
    }
}

private fun resolveDesktopApiBaseUrl(): String =
    System.getProperty(PROPERTY_API_BASE_URL)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(ENV_API_BASE_URL)?.trim()?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_DESKTOP_API_BASE_URL

private fun handleDesktopShortcut(
    modifierPressed: Boolean,
    key: Key,
    rootComponent: DefaultRootComponent,
): Boolean {
    if (modifierPressed) {
        val target = when (key) {
            Key.One -> TopLevelDestination.PROFILE
            Key.Two -> TopLevelDestination.PROJECTS
            Key.Three -> TopLevelDestination.WRITEUPS
            Key.Four -> TopLevelDestination.RESUME_DOCS
            else -> null
        }
        if (target != null) {
            rootComponent.selectDestination(target)
            return true
        }
    }
    if (key == Key.Escape && rootComponent.childStack.value.backStack.isNotEmpty()) {
        rootComponent.navigateBack()
        return true
    }
    return false
}

private class DesktopPlatformActions : PlatformActions {
    override fun openExternalUrl(url: String) {
        browseSafely(url)
    }

    override fun triggerDownload(url: String, filename: String) {
        browseSafely(url)
    }

    private fun browseSafely(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
}
