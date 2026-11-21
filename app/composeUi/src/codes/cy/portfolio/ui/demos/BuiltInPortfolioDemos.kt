package codes.cy.portfolio.ui.demos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.InputModality
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import kotlin.math.roundToInt

object AdaptiveBreakpointsDemo : PortfolioDemo {
    private const val MIN_WIDTH_DP: Float = 320f
    private const val MAX_WIDTH_DP: Float = 1800f
    private const val INITIAL_WIDTH_DP: Float = 1280f
    private val PRESET_WIDTHS_DP: List<Int> = listOf(400, 700, 960, 1360, 1720)

    override val id: String = "adaptive-breakpoints"
    override val title: String = "Material 3 Adaptive Breakpoints & ChildPanels"
    override val summary: String =
        "Drag the simulated window width across all 5 M3 dp tiers to inspect live chrome, grid columns, and Decompose ChildPanels modes."

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content(modifier: Modifier) {
        var simulatedWidth by remember { mutableFloatStateOf(INITIAL_WIDTH_DP) }
        val widthInt = simulatedWidth.roundToInt()
        val tier = AdaptiveLayoutSpec.viewportTierForWidthDp(widthInt)
        val spec = AdaptiveLayoutSpec.from(tier = tier, modality = InputModality.POINTER)

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Simulated Width: ${widthInt}dp",
                    style = MaterialTheme.typography.titleMedium,
                )
                MetadataMonoTag(label = tier.name)
            }

            Slider(
                value = simulatedWidth,
                onValueChange = { simulatedWidth = it },
                valueRange = MIN_WIDTH_DP..MAX_WIDTH_DP,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PRESET_WIDTHS_DP.forEach { preset ->
                    FilterChip(
                        selected = widthInt == preset,
                        onClick = { simulatedWidth = preset.toFloat() },
                        label = { Text("${preset}dp") },
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetadataMonoTag(label = "Chrome: ${spec.navigationChrome.name}")
                MetadataMonoTag(label = "Projects: ${spec.projectsPanelMode.name}")
                MetadataMonoTag(label = "Writeups: ${spec.writeupsPanelMode.name}")
                MetadataMonoTag(label = "Grid: ${spec.gridColumns} col")
                MetadataMonoTag(label = "Measure: <= ${spec.maxReadingMeasureDp}dp")
            }

            PaneWireframePreview(mode = spec.writeupsPanelMode)
        }
    }

    @Composable
    private fun PaneWireframePreview(mode: ChildPanelsMode) {
        val weights = when (mode) {
            ChildPanelsMode.SINGLE -> listOf("Active Pane (100%)" to 1f)
            ChildPanelsMode.DUAL -> listOf("List (35%)" to 0.35f, "Reader (65%)" to 0.65f)
            ChildPanelsMode.TRIPLE -> listOf("List (26%)" to 0.26f, "Reader (54%)" to 0.54f, "TOC Extra (20%)" to 0.20f)
        }
        val editorialColors = PortfolioThemeDefaults.colors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            weights.forEach { (label, weight) ->
                Surface(
                    modifier = Modifier
                        .weight(weight)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, editorialColors.hairlineBorder),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

object StateFlowUdfDemo : PortfolioDemo {
    private enum class DemoIntent(val label: String) {
        LOAD("Load"),
        FILTER_KOTLIN("Filter('kotlin')"),
        OPEN_WRITEUP("Open('decompose-flowmvi')"),
        ERROR("Simulate404"),
    }

    private data class UdfSnapshot(
        val phase: String,
        val activeTag: String?,
        val selectedSlug: String?,
        val lastAction: String,
    )

    override val id: String = "state-flow-udf"
    override val title: String = "FlowMVI Unidirectional State & Action Cycle"
    override val summary: String =
        "Dispatch immutable intents to observe deterministic State transitions and one-shot Decompose navigation Actions."

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content(modifier: Modifier) {
        var snapshot by remember {
            mutableStateOf(
                UdfSnapshot(
                    phase = "Content(2 writeups)",
                    activeTag = null,
                    selectedSlug = null,
                    lastAction = "None",
                ),
            )
        }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DemoIntent.entries.forEach { intent ->
                    FilterChip(
                        selected = false,
                        onClick = { snapshot = reduceDemoIntent(snapshot, intent) },
                        label = { Text(intent.label) },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PortfolioThemeDefaults.colors.hairlineBorder),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = "State.phase = ${snapshot.phase}", style = MaterialTheme.typography.labelMedium)
                    Text(text = "State.activeTag = ${snapshot.activeTag ?: "null"}", style = MaterialTheme.typography.labelMedium)
                    Text(text = "State.selectedSlug = ${snapshot.selectedSlug ?: "null"}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "Last Action -> ${snapshot.lastAction}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    private fun reduceDemoIntent(
        current: UdfSnapshot,
        intent: DemoIntent,
    ): UdfSnapshot =
        when (intent) {
            DemoIntent.LOAD -> UdfSnapshot(
                phase = "Content(2 writeups)",
                activeTag = null,
                selectedSlug = current.selectedSlug,
                lastAction = "AnalyticsEventSender(PAGE_VIEW)",
            )

            DemoIntent.FILTER_KOTLIN -> current.copy(
                phase = "Content(filtered)",
                activeTag = "kotlin",
                lastAction = "Query(tag='kotlin')",
            )

            DemoIntent.OPEN_WRITEUP -> current.copy(
                selectedSlug = "decompose-flowmvi",
                lastAction = "WriteupsAction.OpenWriteup('decompose-flowmvi')",
            )

            DemoIntent.ERROR -> UdfSnapshot(
                phase = "Error(ApiError.NotFound)",
                activeTag = current.activeTag,
                selectedSlug = null,
                lastAction = "Display fallback banner",
            )
        }
}

object PaginationGuardDemo : PortfolioDemo {
    private data class QueryPreset(
        val label: String,
        val page: Int,
        val pageSize: Int,
    )

    private const val OVERFLOW_PAGE: Int = 21_474_838

    private val PRESETS: List<QueryPreset> = listOf(
        QueryPreset("Page 1 / 20", QueryOptions.DEFAULT_PAGE, QueryOptions.DEFAULT_PAGE_SIZE),
        QueryPreset("Page 4 / 50", 4, 50),
        QueryPreset("Invalid Page 0", 0, 20),
        QueryPreset("CWE-190 Overflow", OVERFLOW_PAGE, QueryOptions.MAX_PAGE_SIZE),
    )

    override val id: String = "pagination-guard"
    override val title: String = "Stateless Pagination & CWE-190 Overflow Guard"
    override val summary: String =
        "Tests //core QueryOptions.calculateOffsetOrNull() bounds before executing PostgreSQL LIMIT/OFFSET queries."

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content(modifier: Modifier) {
        var selectedPreset by remember { mutableStateOf(PRESETS.first()) }
        val options = QueryOptions(page = selectedPreset.page, pageSize = selectedPreset.pageSize)
        val calculatedOffset = options.calculateOffsetOrNull()
        val editorialColors = PortfolioThemeDefaults.colors

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PRESETS.forEach { preset ->
                    FilterChip(
                        selected = preset == selectedPreset,
                        onClick = { selectedPreset = preset },
                        label = { Text(preset.label) },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, editorialColors.hairlineBorder),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "QueryOptions(page = ${options.page}, pageSize = ${options.pageSize})",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (calculatedOffset != null) {
                        Text(
                            text = "VALID -> OFFSET $calculatedOffset LIMIT ${options.normalizedPageSize}",
                            style = MaterialTheme.typography.labelMedium,
                            color = editorialColors.statusActive,
                        )
                    } else {
                        Text(
                            text = "BLOCKED -> 400 ApiError.ValidationError (32-bit offset overflow / bounds violation)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
