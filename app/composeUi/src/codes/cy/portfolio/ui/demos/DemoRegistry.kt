package codes.cy.portfolio.ui.demos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioTheme
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import codes.cy.portfolio.ui.theme.PortfolioThemeMode

interface PortfolioDemo {
    val id: String
    val title: String
    val summary: String

    @Composable
    fun Content(modifier: Modifier)
}

/**
 * Compile-time registry of interactive Compose Multiplatform demonstrations referenced
 * inside Markdown articles via ` ```demo:<id> ` fences (shared across Phase 4 Compose & Phase 5 iOS islands).
 */
object DemoRegistry {
    private val demosById: Map<String, PortfolioDemo> =
        listOf(
            AdaptiveBreakpointsDemo,
            StateFlowUdfDemo,
            PaginationGuardDemo,
        ).associateBy { it.id }

    val availableDemoIds: Set<String>
        get() = demosById.keys

    fun resolve(demoId: String): PortfolioDemo? = demosById[demoId.trim()]

    @Composable
    fun DemoIslandCard(
        demoId: String,
        modifier: Modifier = Modifier,
    ) {
        val editorialColors = PortfolioThemeDefaults.colors
        val resolvedDemo = resolve(demoId)

        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = editorialColors.accentSurface,
            border = BorderStroke(1.dp, editorialColors.hairlineBorder),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = PortfolioIcons.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = resolvedDemo?.title ?: "Interactive Demo",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "demo:$demoId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Text(
                    text = resolvedDemo?.summary
                        ?: "Interactive demo '$demoId' is not registered in this client build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (resolvedDemo != null) {
                    HorizontalDivider(color = editorialColors.hairlineBorder)
                    resolvedDemo.Content(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Self-contained themed composable entry point for Phase 5 iOS `ComposeUIViewController` demo islands.
 */
@Composable
fun DemoIslandEntryPoint(
    demoId: String,
    darkTheme: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mode = if (darkTheme) PortfolioThemeMode.DARK else PortfolioThemeMode.LIGHT
    PortfolioTheme(mode = mode) {
        DemoRegistry.DemoIslandCard(
            demoId = demoId,
            modifier = modifier,
        )
    }
}
