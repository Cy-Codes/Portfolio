package codes.cy.portfolio.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.admin.features.writeups.AdminWriteupsIntent
import codes.cy.portfolio.admin.features.writeups.AdminWriteupsState
import codes.cy.portfolio.admin.features.writeups.WriteupFormDraft
import codes.cy.portfolio.admin.navigation.AdminWriteupsComponent
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.EmptyStateView
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.demos.DemoRegistry
import codes.cy.portfolio.ui.markdown.PortfolioMarkdownView
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.platform.displayLabel
import codes.cy.portfolio.ui.platform.formatShortDate
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.extensions.compose.subscribeAsState

private enum class WriteupEditorPaneMode(val label: String) {
    SPLIT("Split View"),
    EDITOR_ONLY("Markdown Only"),
    PREVIEW_ONLY("Live Preview"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminWriteupsScreen(
    component: AdminWriteupsComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val scrollState = rememberScrollState()
    var paneMode by remember(adaptiveSpec.gridColumns) {
        mutableStateOf(
            if (adaptiveSpec.gridColumns >= 2) WriteupEditorPaneMode.SPLIT else WriteupEditorPaneMode.EDITOR_ONLY,
        )
    }

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
                    title = "Writeups CMS & Live Preview",
                    subtitle = "Author technical articles with pixel-identical Compose Markdown, Highlights syntax, and interactive DemoRegistry islands.",
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { component.onIntent(AdminWriteupsIntent.NewWriteup) }) {
                        Text("New Writeup")
                    }
                    IconButton(onClick = { component.onIntent(AdminWriteupsIntent.Load) }) {
                        Icon(PortfolioIcons.Refresh, contentDescription = "Refresh writeups")
                    }
                }
            }

            AdminFeedbackBanner(
                errorMessage = state.errorMessage,
                statusMessage = state.statusMessage,
                onDismiss = { component.onIntent(AdminWriteupsIntent.DismissFeedback) },
            )

            WriteupListSection(
                state = state,
                onIntent = component::onIntent,
            )

            WriteupEditorAndPreviewCard(
                state = state,
                paneMode = paneMode,
                onPaneModeChange = { paneMode = it },
                platformActions = platformActions,
                onIntent = component::onIntent,
            )
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
private fun WriteupListSection(
    state: AdminWriteupsState,
    onIntent: (AdminWriteupsIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.statusFilter == null,
                onClick = { onIntent(AdminWriteupsIntent.FilterByStatus(null)) },
                label = { Text("All (${state.items.size})") },
            )
            WriteupStatus.entries.forEach { st ->
                FilterChip(
                    selected = state.statusFilter == st,
                    onClick = { onIntent(AdminWriteupsIntent.FilterByStatus(st)) },
                    label = { Text(st.displayLabel()) },
                )
            }
        }

        when {
            state.isLoadingList && state.items.isEmpty() -> LoadingStateView(message = "Loading writeups...")
            state.filteredItems.isEmpty() -> EmptyStateView(
                title = "No writeups match this filter",
                subtitle = "Switch filters above or draft a new writeup below.",
            )
            else -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.filteredItems.forEach { writeup ->
                        EditorialCard(
                            selected = state.draft.editingId == writeup.id,
                            onClick = { onIntent(AdminWriteupsIntent.SelectWriteup(writeup.id)) },
                            modifier = Modifier.widthIn(min = 250.dp, max = 360.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WriteupStatusBadge(writeup.status)
                                IconButton(onClick = { onIntent(AdminWriteupsIntent.DeleteWriteup(writeup.id)) }) {
                                    Icon(AdminIcons.Trash, contentDescription = "Delete writeup")
                                }
                            }
                            Text(writeup.title, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MetadataMonoTag(label = writeup.slug)
                                MetadataMonoTag(label = "${writeup.readingTimeMin}m")
                                writeup.publishedAt?.let { published ->
                                    MetadataMonoTag(label = published.formatShortDate())
                                }
                            }
                        }
                    }
                }
                AdminPaginationRow(
                    page = state.page,
                    totalPages = state.totalPages,
                    totalCount = state.totalCount,
                    isLoading = state.isLoadingList,
                    onPageChange = { onIntent(AdminWriteupsIntent.GoToPage(it)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteupEditorAndPreviewCard(
    state: AdminWriteupsState,
    paneMode: WriteupEditorPaneMode,
    onPaneModeChange: (WriteupEditorPaneMode) -> Unit,
    platformActions: PlatformActions,
    onIntent: (AdminWriteupsIntent) -> Unit,
) {
    val draft = state.draft
    val updateDraft: (WriteupFormDraft) -> Unit = { next ->
        onIntent(AdminWriteupsIntent.UpdateDraft(next))
    }

    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (draft.editingId == null) "New Writeup" else "Editing '${draft.slug}'",
                    style = MaterialTheme.typography.titleLarge,
                )
                WriteupStatusBadge(draft.status)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = { onIntent(AdminWriteupsIntent.SaveWithStatus(WriteupStatus.DRAFT)) },
                    enabled = !state.isSaving && !state.isLoadingDetail,
                ) {
                    Text("Save Draft")
                }
                Button(
                    onClick = { onIntent(AdminWriteupsIntent.SaveWithStatus(WriteupStatus.PUBLISHED)) },
                    enabled = !state.isSaving && !state.isLoadingDetail,
                ) {
                    Text(if (state.isSaving) "Saving..." else "Publish")
                }
                OutlinedButton(
                    onClick = { onIntent(AdminWriteupsIntent.SaveWithStatus(WriteupStatus.ARCHIVED)) },
                    enabled = !state.isSaving && !state.isLoadingDetail,
                ) {
                    Text("Archive")
                }
            }
        }

        if (state.isLoadingDetail) {
            LoadingStateView(message = "Loading article body...")
            return@EditorialCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { updateDraft(draft.copy(title = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Title *") },
            )
            OutlinedTextField(
                value = draft.slug,
                onValueChange = { updateDraft(draft.copy(slug = it)) },
                modifier = Modifier.weight(0.85f),
                singleLine = true,
                label = { Text("Slug *") },
            )
            TextButton(onClick = { onIntent(AdminWriteupsIntent.AutoSlugFromTitle) }) {
                Text("Slugify")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft.subtitle,
                onValueChange = { updateDraft(draft.copy(subtitle = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Subtitle") },
            )
            OutlinedTextField(
                value = draft.tagsInput,
                onValueChange = { updateDraft(draft.copy(tagsInput = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Tags (comma-separated)") },
            )
            OutlinedTextField(
                value = draft.readingTimeMinInput,
                onValueChange = { updateDraft(draft.copy(readingTimeMinInput = it)) },
                modifier = Modifier.weight(0.35f),
                singleLine = true,
                label = { Text("Min") },
            )
            TextButton(onClick = { onIntent(AdminWriteupsIntent.AutoEstimateReadingTime) }) {
                Text("Estimate")
            }
        }

        OutlinedTextField(
            value = draft.summary,
            onValueChange = { updateDraft(draft.copy(summary = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Summary *") },
        )

        DocumentAttachmentSelector(
            label = "Cover Asset",
            documents = state.availableDocuments,
            selectedDocumentId = draft.coverDocumentId,
            onSelect = { docId -> updateDraft(draft.copy(coverDocumentId = docId)) },
        )

        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Insert Demo Island:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                DemoRegistry.availableDemoIds.sorted().forEach { demoId ->
                    OutlinedButton(onClick = { onIntent(AdminWriteupsIntent.InsertDemoSnippet(demoId)) }) {
                        Text("+ demo:$demoId")
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WriteupEditorPaneMode.entries.forEach { mode ->
                    FilterChip(
                        selected = paneMode == mode,
                        onClick = { onPaneModeChange(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        val markdownEditorPane: @Composable (Modifier) -> Unit = { paneMod ->
            OutlinedTextField(
                value = draft.bodyMarkdown,
                onValueChange = { updateDraft(draft.copy(bodyMarkdown = it)) },
                modifier = paneMod.heightIn(min = 420.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                label = { Text("Article Body Markdown *") },
                placeholder = { Text("# Heading\n\nWrite Markdown or insert ```demo:adaptive-breakpoints```...") },
            )
        }

        val livePreviewPane: @Composable (Modifier) -> Unit = { paneMod ->
            EditorialCard(modifier = paneMod.heightIn(min = 420.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Pixel-Identical Public Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MetadataMonoTag(label = "PortfolioMarkdownView + DemoRegistry")
                }
                HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)
                if (draft.title.isNotBlank()) {
                    Text(
                        text = draft.title,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                draft.subtitle.trim().takeIf { it.isNotEmpty() }?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PortfolioMarkdownView(
                    markdown = draft.bodyMarkdown.ifBlank { "_Start writing Markdown to preview Highlights & interactive DemoRegistry islands._" },
                    platformActions = platformActions,
                )
            }
        }

        when (paneMode) {
            WriteupEditorPaneMode.SPLIT -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                markdownEditorPane(Modifier.weight(1f))
                livePreviewPane(Modifier.weight(1f))
            }

            WriteupEditorPaneMode.EDITOR_ONLY -> markdownEditorPane(Modifier.fillMaxWidth())
            WriteupEditorPaneMode.PREVIEW_ONLY -> livePreviewPane(Modifier.fillMaxWidth())
        }
    }
}
