package codes.cy.portfolio.ui

import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.InputModality
import codes.cy.portfolio.ui.adaptive.NavigationChrome
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveLayoutSpecTest {

    @Test
    fun `viewportTierForWidthDp maps all five M3 breakpoint intervals accurately`() {
        assertEquals(ViewportTier.COMPACT, AdaptiveLayoutSpec.viewportTierForWidthDp(320))
        assertEquals(ViewportTier.COMPACT, AdaptiveLayoutSpec.viewportTierForWidthDp(599))
        assertEquals(ViewportTier.MEDIUM, AdaptiveLayoutSpec.viewportTierForWidthDp(600))
        assertEquals(ViewportTier.MEDIUM, AdaptiveLayoutSpec.viewportTierForWidthDp(839))
        assertEquals(ViewportTier.EXPANDED, AdaptiveLayoutSpec.viewportTierForWidthDp(840))
        assertEquals(ViewportTier.EXPANDED, AdaptiveLayoutSpec.viewportTierForWidthDp(1199))
        assertEquals(ViewportTier.LARGE, AdaptiveLayoutSpec.viewportTierForWidthDp(1200))
        assertEquals(ViewportTier.LARGE, AdaptiveLayoutSpec.viewportTierForWidthDp(1599))
        assertEquals(ViewportTier.EXTRA_LARGE, AdaptiveLayoutSpec.viewportTierForWidthDp(1600))
        assertEquals(ViewportTier.EXTRA_LARGE, AdaptiveLayoutSpec.viewportTierForWidthDp(1920))
    }

    @Test
    fun `AdaptiveLayoutSpec configures chrome panels and grid density per viewport tier`() {
        val compact = AdaptiveLayoutSpec.from(ViewportTier.COMPACT, InputModality.TOUCH)
        assertEquals(NavigationChrome.BOTTOM_BAR, compact.navigationChrome)
        assertEquals(ChildPanelsMode.SINGLE, compact.projectsPanelMode)
        assertEquals(ChildPanelsMode.SINGLE, compact.writeupsPanelMode)
        assertEquals(1, compact.gridColumns)
        assertEquals(760, compact.maxReadingMeasureDp)

        val medium = AdaptiveLayoutSpec.from(ViewportTier.MEDIUM, InputModality.TOUCH)
        assertEquals(NavigationChrome.NAVIGATION_RAIL, medium.navigationChrome)
        assertEquals(ChildPanelsMode.SINGLE, medium.projectsPanelMode)
        assertEquals(ChildPanelsMode.SINGLE, medium.writeupsPanelMode)
        assertEquals(2, medium.gridColumns)

        val expanded = AdaptiveLayoutSpec.from(ViewportTier.EXPANDED, InputModality.POINTER)
        assertEquals(NavigationChrome.NAVIGATION_RAIL, expanded.navigationChrome)
        assertEquals(ChildPanelsMode.DUAL, expanded.projectsPanelMode)
        assertEquals(ChildPanelsMode.DUAL, expanded.writeupsPanelMode)
        assertEquals(2, expanded.gridColumns)

        val large = AdaptiveLayoutSpec.from(ViewportTier.LARGE, InputModality.POINTER)
        assertEquals(NavigationChrome.PERMANENT_DRAWER, large.navigationChrome)
        assertEquals(ChildPanelsMode.DUAL, large.projectsPanelMode)
        assertEquals(ChildPanelsMode.TRIPLE, large.writeupsPanelMode)
        assertEquals(2, large.gridColumns)

        val extraLarge = AdaptiveLayoutSpec.from(ViewportTier.EXTRA_LARGE, InputModality.POINTER)
        assertEquals(NavigationChrome.PERMANENT_DRAWER, extraLarge.navigationChrome)
        assertEquals(ChildPanelsMode.DUAL, extraLarge.projectsPanelMode)
        assertEquals(ChildPanelsMode.TRIPLE, extraLarge.writeupsPanelMode)
        assertEquals(3, extraLarge.gridColumns)
    }
}
