package codes.cy.portfolio.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.admin.features.stats.AdminStatsIntent
import codes.cy.portfolio.admin.navigation.AdminStatsComponent
import codes.cy.portfolio.core.model.NamedCountDto
import codes.cy.portfolio.core.model.PathStatsDto
import codes.cy.portfolio.core.model.StatsOverviewDto
import codes.cy.portfolio.core.model.StatsTimeRange
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.EmptyStateView
import codes.cy.portfolio.ui.components.ErrorStateView
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminStatsScreen(
    component: AdminStatsComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = adaptiveSpec.contentHorizontalPaddingDp.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Cookieless Analytics Overview",
                    subtitle = "Daily-salted visitor hashes, writeup reads, downloads, referrers, and viewport tiers.",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { component.onIntent(AdminStatsIntent.Refresh) }) {
                    Icon(PortfolioIcons.Refresh, contentDescription = "Refresh analytics")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatsTimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { component.onIntent(AdminStatsIntent.SelectRange(range)) },
                        label = { Text(range.displayLabel()) },
                    )
                }
            }

            val errMsg = state.errorMessage
            val overview = state.overview
            when {
                state.isLoading && overview == null -> LoadingStateView(message = "Aggregating analytics rollups...")
                errMsg != null && overview == null -> ErrorStateView(
                    message = errMsg,
                    onRetry = { component.onIntent(AdminStatsIntent.Refresh) },
                )
                overview != null -> {
                    if (errMsg != null) {
                        AdminFeedbackBanner(
                            errorMessage = errMsg,
                            statusMessage = null,
                            onDismiss = { component.onIntent(AdminStatsIntent.Refresh) },
                        )
                    }
                    StatsDashboardContent(
                        overview = overview,
                        gridColumns = adaptiveSpec.gridColumns,
                    )
                }
            }
        }

        PlatformVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsDashboardContent(
    overview: StatsOverviewDto,
    gridColumns: Int,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val kpis = listOf(
            "Total Events" to overview.totalEvents,
            "Unique Visitors" to overview.uniqueVisitors,
            "Page Views" to overview.pageViews,
            "Writeup Reads" to overview.writeupReads,
            "Project Clicks" to overview.projectClicks,
            "Doc Downloads" to overview.documentDownloads,
        )
        kpis.forEach { (label, count) ->
            EditorialCard(
                modifier = Modifier
                    .widthIn(min = if (gridColumns == 1) 148.dp else 180.dp)
                    .weight(1f, fill = false),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    TopPathsCard(paths = overview.topPaths)

    val breakdownCards = listOf(
        "Top Referrers" to overview.topReferrers,
        "By Client Platform" to overview.byPlatform,
        "By Viewport Tier" to overview.byViewportTier,
    )

    if (gridColumns >= 2) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            breakdownCards.forEach { (title, items) ->
                NamedCountBreakdownCard(
                    title = title,
                    items = items,
                    modifier = Modifier
                        .widthIn(min = 260.dp)
                        .weight(1f),
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            breakdownCards.forEach { (title, items) ->
                NamedCountBreakdownCard(
                    title = title,
                    items = items,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TopPathsCard(paths: List<PathStatsDto>) {
    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Top Paths & Content",
            style = MaterialTheme.typography.titleLarge,
        )
        if (paths.isEmpty()) {
            EmptyStateView(
                title = "No path events in range",
                subtitle = "Public views, writeup reads, and downloads appear here once recorded.",
            )
        } else {
            paths.forEachIndexed { idx, row ->
                if (idx > 0) {
                    HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = row.path,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        MetadataMonoTag(label = row.eventType.name)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetadataMonoTag(
                            label = "${row.eventCount} events",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        MetadataMonoTag(label = "${row.uniqueVisitors} unique")
                    }
                }
            }
        }
    }
}

@Composable
private fun NamedCountBreakdownCard(
    title: String,
    items: List<NamedCountDto>,
    modifier: Modifier = Modifier,
) {
    EditorialCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (items.isEmpty()) {
            Text(
                text = "No events recorded for this range.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val maxCount = items.maxOf { it.count }.coerceAtLeast(1L)
            items.forEach { item ->
                val fraction = (item.count.toFloat() / maxCount.toFloat()).coerceIn(0.05f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = item.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(999.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

private fun StatsTimeRange.displayLabel(): String =
    when (this) {
        StatsTimeRange.SEVEN_DAYS -> "Last 7 Days"
        StatsTimeRange.THIRTY_DAYS -> "Last 30 Days"
        StatsTimeRange.NINETY_DAYS -> "Last 90 Days"
        StatsTimeRange.ALL -> "All Time"
    }
