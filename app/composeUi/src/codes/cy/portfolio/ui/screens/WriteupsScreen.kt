package codes.cy.portfolio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.presentation.features.readarticle.ReadArticleIntent
import codes.cy.portfolio.presentation.features.readarticle.ReadArticleState
import codes.cy.portfolio.presentation.features.readarticle.TocHeading
import codes.cy.portfolio.presentation.features.writeups.WriteupsIntent
import codes.cy.portfolio.presentation.features.writeups.WriteupsState
import codes.cy.portfolio.presentation.navigation.WriteupsPanelsComponent
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformLazyVerticalScrollbar
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.EmptyStateView
import codes.cy.portfolio.ui.components.ErrorStateView
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.markdown.PortfolioMarkdownView
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.platform.formatShortDate
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.panels.ChildPanels
import com.arkivanov.decompose.extensions.compose.experimental.panels.HorizontalChildPanelsLayout
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.panels.ChildPanelsMode

private const val DUAL_LIST_WEIGHT: Float = 0.35f
private const val DUAL_READER_WEIGHT: Float = 0.65f
private const val TRIPLE_LIST_WEIGHT: Float = 0.26f
private const val TRIPLE_READER_WEIGHT: Float = 0.54f
private const val TRIPLE_EXTRA_WEIGHT: Float = 0.20f
private const val TOC_INDENT_PER_LEVEL_DP: Int = 10

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun WriteupsScreen(
    component: WriteupsPanelsComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(adaptiveSpec.writeupsPanelMode) {
        component.setMode(adaptiveSpec.writeupsPanelMode)
    }

    val panelsState by component.panels.subscribeAsState()
    val hasSupportingExtraPane = panelsState.extra != null

    ChildPanels(
        panels = component.panels,
        mainChild = { child ->
            WriteupsListPane(
                child = child.instance,
                horizontalPaddingDp = adaptiveSpec.contentHorizontalPaddingDp,
                modifier = Modifier.fillMaxSize(),
            )
        },
        detailsChild = { child ->
            ArticleReaderPane(
                child = child.instance,
                panelMode = adaptiveSpec.writeupsPanelMode,
                maxReadingMeasureDp = adaptiveSpec.maxReadingMeasureDp,
                hasSupportingExtraPane = hasSupportingExtraPane,
                onToggleExtraPane = { component.setSupportingTocVisible(!hasSupportingExtraPane) },
                platformActions = platformActions,
                modifier = Modifier.fillMaxSize(),
            )
        },
        extraChild = { child ->
            ArticleTocExtraPane(
                child = child.instance,
                modifier = Modifier.fillMaxSize(),
            )
        },
        modifier = modifier.fillMaxSize(),
        secondPanelPlaceholder = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateView(
                    title = "Select an Engineering Writeup",
                    subtitle = "Open any technical note to read prose, syntax-highlighted snippets, and interactive Compose demos.",
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }
        },
        thirdPanelPlaceholder = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = "Outline & interactive demos appear here when an article is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        layout = HorizontalChildPanelsLayout(
            dualWeights = DUAL_LIST_WEIGHT to DUAL_READER_WEIGHT,
            tripleWeights = Triple(TRIPLE_LIST_WEIGHT, TRIPLE_READER_WEIGHT, TRIPLE_EXTRA_WEIGHT),
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteupsListPane(
    child: WriteupsPanelsComponent.WriteupsListChild,
    horizontalPaddingDp: Int,
    modifier: Modifier = Modifier,
) {
    val state by child.state.subscribeAsState()
    val listState = rememberLazyListState()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPaddingDp.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "writeups-header") {
                WriteupsFilterHeader(
                    state = state,
                    onIntent = child::onIntent,
                )
            }

            val errMessage = state.displayErrorMessage
            if (errMessage != null && state.items.isEmpty()) {
                item(key = "writeups-error") {
                    ErrorStateView(
                        message = errMessage,
                        onRetry = { child.onIntent(WriteupsIntent.LoadInitial) },
                    )
                }
            } else if (state.isLoading && state.items.isEmpty()) {
                item(key = "writeups-loading") {
                    LoadingStateView(message = "Loading writeups...")
                }
            } else if (state.items.isEmpty()) {
                item(key = "writeups-empty") {
                    EmptyStateView(
                        title = "No matching writeups",
                        subtitle = "Try broadening your search query or clearing active tag filters.",
                        actionLabel = "Clear Filters",
                        onAction = {
                            child.onIntent(WriteupsIntent.UpdateSearchQuery(""))
                            child.onIntent(WriteupsIntent.SelectTag(null))
                            child.onIntent(WriteupsIntent.SubmitSearch)
                        },
                    )
                }
            } else {
                items(
                    items = state.items,
                    key = { it.id },
                ) { writeup ->
                    WriteupSummaryCard(
                        writeup = writeup,
                        selected = writeup.slug == state.selectedSlug,
                        onClick = { child.onIntent(WriteupsIntent.SelectWriteup(writeup.slug)) },
                    )
                }

                if (state.hasNextPage) {
                    item(key = "writeups-load-more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            OutlinedButton(
                                onClick = { child.onIntent(WriteupsIntent.LoadNextPage) },
                                enabled = !state.isLoading,
                            ) {
                                Text(if (state.isLoading) "Loading more..." else "Load More Writeups")
                            }
                        }
                    }
                }
            }
        }

        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteupsFilterHeader(
    state: WriteupsState,
    onIntent: (WriteupsIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                title = "Engineering Writeups",
                subtitle = "Technical deep-dives with syntax highlighting & interactive demos.",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onIntent(WriteupsIntent.Refresh) }) {
                Icon(PortfolioIcons.Refresh, contentDescription = "Refresh writeups")
            }
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onIntent(WriteupsIntent.UpdateSearchQuery(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Full-text search") },
            leadingIcon = { Icon(PortfolioIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onIntent(WriteupsIntent.UpdateSearchQuery(""))
                            onIntent(WriteupsIntent.SubmitSearch)
                        },
                    ) {
                        Icon(PortfolioIcons.Clear, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onIntent(WriteupsIntent.SubmitSearch) }),
        )

        if (state.availableTags.isNotEmpty() || state.activeTag != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.activeTag == null,
                    onClick = { onIntent(WriteupsIntent.SelectTag(null)) },
                    label = { Text("All Tags") },
                )
                state.availableTags.forEach { tag ->
                    FilterChip(
                        selected = state.activeTag == tag,
                        onClick = {
                            val next = if (state.activeTag == tag) null else tag
                            onIntent(WriteupsIntent.SelectTag(next))
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteupSummaryCard(
    writeup: WriteupSummaryDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    EditorialCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = writeup.title,
            style = MaterialTheme.typography.titleMedium,
        )
        writeup.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = writeup.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetadataMonoTag(label = "${writeup.readingTimeMin} min read")
            writeup.publishedAt?.let { published ->
                MetadataMonoTag(label = published.formatShortDate())
            }
            writeup.tags.forEach { tag ->
                MetadataMonoTag(label = tag)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleReaderPane(
    child: WriteupsPanelsComponent.ReadArticleComponent,
    panelMode: ChildPanelsMode,
    maxReadingMeasureDp: Int,
    hasSupportingExtraPane: Boolean,
    onToggleExtraPane: () -> Unit,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    val state by child.state.subscribeAsState()
    val scrollState = rememberScrollState()
    var inlineTocExpanded by remember(child.slug) { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxReadingMeasureDp.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (panelMode == ChildPanelsMode.SINGLE) {
                        TextButton(onClick = { child.onIntent(ReadArticleIntent.NavigateBack) }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(PortfolioIcons.ArrowBack, contentDescription = null)
                                Text("Writeups")
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (panelMode == ChildPanelsMode.TRIPLE) {
                            OutlinedButton(onClick = onToggleExtraPane) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(PortfolioIcons.Toc, contentDescription = null)
                                    Text(if (hasSupportingExtraPane) "Hide Outline" else "Show Outline")
                                }
                            }
                        }
                        OutlinedButton(onClick = { child.onIntent(ReadArticleIntent.OpenReaderHtmlVersion) }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("HTML Reader")
                                Icon(PortfolioIcons.ExternalLink, contentDescription = null)
                            }
                        }
                    }
                }

                when (val current = state) {
                    is ReadArticleState.Loading -> LoadingStateView(message = "Loading article '${current.slug}'...")
                    is ReadArticleState.Error -> ErrorStateView(
                        message = current.displayMessage,
                        onRetry = { child.onIntent(ReadArticleIntent.Retry) },
                    )

                    is ReadArticleState.Content -> {
                        val writeup = current.writeup
                        Text(
                            text = writeup.title,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        writeup.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MetadataMonoTag(label = "${writeup.readingTimeMin} min read")
                            writeup.publishedAt?.let { published ->
                                MetadataMonoTag(label = "Published ${published.formatShortDate()}")
                            }
                            writeup.tags.forEach { tag ->
                                MetadataMonoTag(label = tag)
                            }
                        }

                        if (panelMode != ChildPanelsMode.TRIPLE && current.tableOfContents.isNotEmpty()) {
                            OutlinedButton(onClick = { inlineTocExpanded = !inlineTocExpanded }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(PortfolioIcons.Toc, contentDescription = null)
                                    Text(if (inlineTocExpanded) "Hide Table of Contents" else "Table of Contents (${current.tableOfContents.size})")
                                }
                            }
                            if (inlineTocExpanded) {
                                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                                    TocListContent(
                                        headings = current.tableOfContents,
                                        demoIds = current.embeddedDemoIds,
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

                        PortfolioMarkdownView(
                            markdown = writeup.bodyMarkdown,
                            platformActions = platformActions,
                        )
                    }
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

@Composable
private fun ArticleTocExtraPane(
    child: WriteupsPanelsComponent.ArticleExtraChild,
    modifier: Modifier = Modifier,
) {
    val headings by child.tocHeadings.subscribeAsState()
    val demoIds by child.embeddedDemoIds.subscribeAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = "Outline",
            subtitle = "Headings & embedded demos",
        )
        TocListContent(headings = headings, demoIds = demoIds)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TocListContent(
    headings: List<TocHeading>,
    demoIds: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (headings.isEmpty()) {
            Text(
                text = "No section headings in this article.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            headings.forEach { heading ->
                val indentDp = ((heading.level - 1).coerceAtLeast(0) * TOC_INDENT_PER_LEVEL_DP).dp
                Text(
                    text = heading.title,
                    style = if (heading.level == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = indentDp),
                )
            }
        }

        if (demoIds.isNotEmpty()) {
            HorizontalDivider(
                color = PortfolioThemeDefaults.colors.hairlineBorder,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = "Embedded Compose Demos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                demoIds.forEach { demoId ->
                    MetadataMonoTag(
                        label = "demo:$demoId",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
