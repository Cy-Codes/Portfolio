package codes.cy.portfolio

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.navigation.DefaultRootComponent
import codes.cy.portfolio.presentation.navigation.PresentationDependencies
import codes.cy.portfolio.ui.PortfolioApp
import codes.cy.portfolio.ui.platform.PlatformActions
import com.arkivanov.decompose.defaultComponentContext

private const val DEBUG_ANDROID_API_BASE_URL: String = "http://10.0.2.2:8080"
private const val PROD_ANDROID_API_BASE_URL: String = "https://cy.codes"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val apiBaseUrl = if (isDebuggable) DEBUG_ANDROID_API_BASE_URL else PROD_ANDROID_API_BASE_URL

        val dependencies = PresentationDependencies.fromBaseUrl(
            baseUrl = apiBaseUrl,
            platform = ClientPlatform.ANDROID,
            viewportTierProvider = { ViewportTier.COMPACT },
        )
        val rootComponent = DefaultRootComponent.fromDeepLinkPath(
            componentContext = defaultComponentContext(),
            dependencies = dependencies,
            initialPath = intent?.data?.toString(),
            initialViewportTier = ViewportTier.COMPACT,
        )
        val platformActions = AndroidPlatformActions(this)

        setContent {
            PortfolioApp(
                rootComponent = rootComponent,
                platformActions = platformActions,
            )
        }
    }
}

private class AndroidPlatformActions(
    private val activity: ComponentActivity,
) : PlatformActions {
    override fun openExternalUrl(url: String) {
        launchBrowser(url)
    }

    override fun triggerDownload(url: String, filename: String) {
        launchBrowser(url)
    }

    private fun launchBrowser(url: String) {
        runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(viewIntent)
        }
    }
}
