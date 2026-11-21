package codes.cy.portfolio.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import codes.cy.portfolio.core.model.ViewportTier
import com.arkivanov.decompose.router.panels.ChildPanelsMode

enum class InputModality {
    POINTER,
    TOUCH,
}

enum class NavigationChrome {
    BOTTOM_BAR,
    NAVIGATION_RAIL,
    PERMANENT_DRAWER,
}

data class AdaptiveLayoutSpec(
    val viewportTier: ViewportTier,
    val modality: InputModality,
    val navigationChrome: NavigationChrome,
    val projectsPanelMode: ChildPanelsMode,
    val writeupsPanelMode: ChildPanelsMode,
    val gridColumns: Int,
    val maxReadingMeasureDp: Int,
    val contentHorizontalPaddingDp: Int,
) {
    companion object {
        const val BREAKPOINT_MEDIUM_DP: Int = 600
        const val BREAKPOINT_EXPANDED_DP: Int = 840
        const val BREAKPOINT_LARGE_DP: Int = 1200
        const val BREAKPOINT_EXTRA_LARGE_DP: Int = 1600

        const val DEFAULT_READING_MEASURE_DP: Int = 760

        private const val COLUMNS_COMPACT: Int = 1
        private const val COLUMNS_STANDARD_WIDE: Int = 2
        private const val COLUMNS_EXTRA_LARGE: Int = 3

        private const val PADDING_COMPACT_DP: Int = 16
        private const val PADDING_MEDIUM_DP: Int = 24
        private const val PADDING_EXPANDED_DP: Int = 28
        private const val PADDING_LARGE_DP: Int = 36
        private const val PADDING_EXTRA_LARGE_DP: Int = 44

        fun viewportTierForWidthDp(widthDp: Int): ViewportTier =
            when {
                widthDp >= BREAKPOINT_EXTRA_LARGE_DP -> ViewportTier.EXTRA_LARGE
                widthDp >= BREAKPOINT_LARGE_DP -> ViewportTier.LARGE
                widthDp >= BREAKPOINT_EXPANDED_DP -> ViewportTier.EXPANDED
                widthDp >= BREAKPOINT_MEDIUM_DP -> ViewportTier.MEDIUM
                else -> ViewportTier.COMPACT
            }

        fun from(
            tier: ViewportTier,
            modality: InputModality = InputModality.TOUCH,
        ): AdaptiveLayoutSpec =
            when (tier) {
                ViewportTier.COMPACT -> AdaptiveLayoutSpec(
                    viewportTier = tier,
                    modality = modality,
                    navigationChrome = NavigationChrome.BOTTOM_BAR,
                    projectsPanelMode = ChildPanelsMode.SINGLE,
                    writeupsPanelMode = ChildPanelsMode.SINGLE,
                    gridColumns = COLUMNS_COMPACT,
                    maxReadingMeasureDp = DEFAULT_READING_MEASURE_DP,
                    contentHorizontalPaddingDp = PADDING_COMPACT_DP,
                )

                ViewportTier.MEDIUM -> AdaptiveLayoutSpec(
                    viewportTier = tier,
                    modality = modality,
                    navigationChrome = NavigationChrome.NAVIGATION_RAIL,
                    projectsPanelMode = ChildPanelsMode.SINGLE,
                    writeupsPanelMode = ChildPanelsMode.SINGLE,
                    gridColumns = COLUMNS_STANDARD_WIDE,
                    maxReadingMeasureDp = DEFAULT_READING_MEASURE_DP,
                    contentHorizontalPaddingDp = PADDING_MEDIUM_DP,
                )

                ViewportTier.EXPANDED -> AdaptiveLayoutSpec(
                    viewportTier = tier,
                    modality = modality,
                    navigationChrome = NavigationChrome.NAVIGATION_RAIL,
                    projectsPanelMode = ChildPanelsMode.DUAL,
                    writeupsPanelMode = ChildPanelsMode.DUAL,
                    gridColumns = COLUMNS_STANDARD_WIDE,
                    maxReadingMeasureDp = DEFAULT_READING_MEASURE_DP,
                    contentHorizontalPaddingDp = PADDING_EXPANDED_DP,
                )

                ViewportTier.LARGE -> AdaptiveLayoutSpec(
                    viewportTier = tier,
                    modality = modality,
                    navigationChrome = NavigationChrome.PERMANENT_DRAWER,
                    projectsPanelMode = ChildPanelsMode.DUAL,
                    writeupsPanelMode = ChildPanelsMode.TRIPLE,
                    gridColumns = COLUMNS_STANDARD_WIDE,
                    maxReadingMeasureDp = DEFAULT_READING_MEASURE_DP,
                    contentHorizontalPaddingDp = PADDING_LARGE_DP,
                )

                ViewportTier.EXTRA_LARGE -> AdaptiveLayoutSpec(
                    viewportTier = tier,
                    modality = modality,
                    navigationChrome = NavigationChrome.PERMANENT_DRAWER,
                    projectsPanelMode = ChildPanelsMode.DUAL,
                    writeupsPanelMode = ChildPanelsMode.TRIPLE,
                    gridColumns = COLUMNS_EXTRA_LARGE,
                    maxReadingMeasureDp = DEFAULT_READING_MEASURE_DP,
                    contentHorizontalPaddingDp = PADDING_EXTRA_LARGE_DP,
                )
            }
    }
}

fun WindowSizeClass.toViewportTier(): ViewportTier =
    when {
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> ViewportTier.EXTRA_LARGE
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> ViewportTier.LARGE
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> ViewportTier.EXPANDED
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> ViewportTier.MEDIUM
        else -> ViewportTier.COMPACT
    }

@Composable
fun rememberAdaptiveLayoutSpec(): AdaptiveLayoutSpec {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val tier = windowSizeClass.toViewportTier()
    return AdaptiveLayoutSpec.from(
        tier = tier,
        modality = defaultInputModality,
    )
}
