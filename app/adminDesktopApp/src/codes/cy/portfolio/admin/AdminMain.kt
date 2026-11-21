package codes.cy.portfolio.admin

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
import codes.cy.portfolio.admin.navigation.AdminDependencies
import codes.cy.portfolio.admin.navigation.AdminSection
import codes.cy.portfolio.admin.navigation.DefaultAdminRootComponent
import codes.cy.portfolio.ui.platform.PlatformActions
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import java.awt.Desktop
import java.awt.Dimension
import java.net.URI

private const val DEFAULT_ADMIN_API_BASE_URL: String = "http://127.0.0.1:8081"
private const val PROPERTY_ADMIN_API_BASE_URL: String = "portfolio.adminApiBaseUrl"
private const val ENV_ADMIN_API_BASE_URL: String = "ADMIN_API_BASE_URL"
private const val PROPERTY_ADMIN_DEV_TOKEN: String = "portfolio.adminDevToken"
private const val ENV_ADMIN_DEV_TOKEN: String = "ADMIN_DEV_TOKEN"
private const val INITIAL_WINDOW_WIDTH_DP: Int = 1440
private const val INITIAL_WINDOW_HEIGHT_DP: Int = 920
private const val MIN_WINDOW_WIDTH_PX: Int = 420
private const val MIN_WINDOW_HEIGHT_PX: Int = 560

fun main() = application {
    val lifecycle = remember { LifecycleRegistry() }
    val rootComponent = remember(lifecycle) {
        val initialToken = resolveOptionalConfig(PROPERTY_ADMIN_DEV_TOKEN, ENV_ADMIN_DEV_TOKEN).orEmpty()
        val dependencies = AdminDependencies.fromBaseUrl(
            baseUrl = resolveAdminApiBaseUrl(),
            initialToken = initialToken,
            autoVerifyInitialToken = initialToken.isNotBlank(),
        )
        DefaultAdminRootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            dependencies = dependencies,
        )
    }
    val platformActions = remember { DesktopAdminPlatformActions() }
    val windowState = rememberWindowState(
        width = INITIAL_WINDOW_WIDTH_DP.dp,
        height = INITIAL_WINDOW_HEIGHT_DP.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "CY · Portfolio Admin",
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                handleAdminDesktopShortcut(event.isCtrlPressed || event.isMetaPressed, event.key, rootComponent)
            }
        },
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(MIN_WINDOW_WIDTH_PX, MIN_WINDOW_HEIGHT_PX)
        }
        LifecycleController(lifecycle, windowState, LocalWindowInfo.current)
        AdminApp(
            rootComponent = rootComponent,
            platformActions = platformActions,
        )
    }
}

private fun resolveAdminApiBaseUrl(): String =
    resolveOptionalConfig(PROPERTY_ADMIN_API_BASE_URL, ENV_ADMIN_API_BASE_URL)
        ?: DEFAULT_ADMIN_API_BASE_URL

private fun resolveOptionalConfig(propertyKey: String, envKey: String): String? =
    System.getProperty(propertyKey)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(envKey)?.trim()?.takeIf { it.isNotEmpty() }

private fun handleAdminDesktopShortcut(
    modifierPressed: Boolean,
    key: Key,
    rootComponent: DefaultAdminRootComponent,
): Boolean {
    if (!modifierPressed) return false
    val section = when (key) {
        Key.One -> AdminSection.STATS
        Key.Two -> AdminSection.CAREER
        Key.Three -> AdminSection.PROJECTS
        Key.Four -> AdminSection.WRITEUPS
        Key.Five -> AdminSection.DOCUMENTS
        else -> null
    } ?: return false
    rootComponent.selectSection(section)
    return true
}

private class DesktopAdminPlatformActions : PlatformActions {
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
