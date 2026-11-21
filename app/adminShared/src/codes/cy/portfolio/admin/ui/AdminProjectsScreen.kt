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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.admin.features.projects.AdminProjectsIntent
import codes.cy.portfolio.admin.features.projects.AdminProjectsState
import codes.cy.portfolio.admin.features.projects.ProjectFormDraft
import codes.cy.portfolio.admin.navigation.AdminProjectsComponent
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.EmptyStateView
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.markdown.PortfolioMarkdownView
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.platform.displayLabel
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminProjectsScreen(
    component: AdminProjectsComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val scrollState = rememberScrollState()
    var showPreview by remember { mutableStateOf(true) }

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
                    title = "Projects CMS",
                    subtitle = "Create and edit case studies with live PortfolioMarkdownView preview.",
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { component.onIntent(AdminProjectsIntent.NewProject) }) {
                        Text("New Project")
                    }
                    IconButton(onClick = { component.onIntent(AdminProjectsIntent.Load) }) {
                        Icon(PortfolioIcons.Refresh, contentDescription = "Refresh projects")
                    }
                }
            }

            AdminFeedbackBanner(
                errorMessage = state.errorMessage,
                statusMessage = state.statusMessage,
                onDismiss = { component.onIntent(AdminProjectsIntent.DismissFeedback) },
            )

            ProjectInventorySection(
                state = state,
                onIntent = component::onIntent,
            )

            ProjectEditorSection(
                state = state,
                isWide = adaptiveSpec.gridColumns >= 2,
                showPreview = showPreview,
                onTogglePreview = { showPreview = !showPreview },
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
private fun ProjectInventorySection(
    state: AdminProjectsState,
    onIntent: (AdminProjectsIntent) -> Unit,
) {
    if (state.isLoadingList && state.items.isEmpty()) {
        LoadingStateView(message = "Loading projects...")
        return
    }
    if (state.items.isEmpty()) {
        EmptyStateView(
            title = "No projects yet",
            subtitle = "Use the editor below to publish your first engineering case study.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.items.forEach { project ->
                EditorialCard(
                    selected = state.draft.editingId == project.id,
                    onClick = { onIntent(AdminProjectsIntent.SelectProject(project.id)) },
                    modifier = Modifier.widthIn(min = 240.dp, max = 360.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProjectStatusBadge(project.status)
                        IconButton(onClick = { onIntent(AdminProjectsIntent.DeleteProject(project.id)) }) {
                            Icon(AdminIcons.Trash, contentDescription = "Delete project")
                        }
                    }
                    Text(project.title, style = MaterialTheme.typography.titleMedium)
                    MetadataMonoTag(label = project.slug)
                }
            }
        }
        AdminPaginationRow(
            page = state.page,
            totalPages = state.totalPages,
            totalCount = state.totalCount,
            isLoading = state.isLoadingList,
            onPageChange = { onIntent(AdminProjectsIntent.GoToPage(it)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectEditorSection(
    state: AdminProjectsState,
    isWide: Boolean,
    showPreview: Boolean,
    onTogglePreview: () -> Unit,
    platformActions: PlatformActions,
    onIntent: (AdminProjectsIntent) -> Unit,
) {
    val draft = state.draft
    val updateDraft: (ProjectFormDraft) -> Unit = { next ->
        onIntent(AdminProjectsIntent.UpdateDraft(next))
    }

    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (draft.editingId == null) "Create Project Case Study" else "Edit '${draft.slug}'",
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTogglePreview) {
                    Text(if (showPreview) "Hide Preview" else "Show Preview")
                }
                Button(
                    onClick = { onIntent(AdminProjectsIntent.SaveProject) },
                    enabled = !state.isSaving && !state.isLoadingDetail,
                ) {
                    Text(if (state.isSaving) "Saving..." else if (draft.editingId == null) "Create Project" else "Save Changes")
                }
            }
        }

        if (state.isLoadingDetail) {
            LoadingStateView(message = "Loading project details...")
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
                modifier = Modifier.weight(0.9f),
                singleLine = true,
                label = { Text("Slug *") },
            )
            TextButton(onClick = { onIntent(AdminProjectsIntent.GenerateSlugFromTitle) }) {
                Text("Slugify")
            }
        }

        OutlinedTextField(
            value = draft.summary,
            onValueChange = { updateDraft(draft.copy(summary = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Summary *") },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft.repoUrl,
                onValueChange = { updateDraft(draft.copy(repoUrl = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Repository URL") },
            )
            OutlinedTextField(
                value = draft.liveUrl,
                onValueChange = { updateDraft(draft.copy(liveUrl = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Live URL") },
            )
            OutlinedTextField(
                value = draft.sortOrderInput,
                onValueChange = { updateDraft(draft.copy(sortOrderInput = it)) },
                modifier = Modifier.weight(0.45f),
                singleLine = true,
                label = { Text("Sort") },
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProjectStatus.entries.forEach { st ->
                FilterChip(
                    selected = draft.status == st,
                    onClick = { updateDraft(draft.copy(status = st)) },
                    label = { Text(st.displayLabel()) },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Featured", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = draft.isFeatured,
                    onCheckedChange = { checked -> updateDraft(draft.copy(isFeatured = checked)) },
                )
            }
        }

        if (state.availableSkills.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.availableSkills.forEach { skill ->
                    FilterChip(
                        selected = skill.id in draft.selectedSkillIds,
                        onClick = { onIntent(AdminProjectsIntent.ToggleSkill(skill.id)) },
                        label = { Text(skill.name) },
                    )
                }
            }
        }

        DocumentAttachmentSelector(
            label = "Cover Asset",
            documents = state.availableDocuments,
            selectedDocumentId = draft.coverDocumentId,
            onSelect = { docId -> updateDraft(draft.copy(coverDocumentId = docId)) },
        )

        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

        val editorPane: @Composable (Modifier) -> Unit = { paneMod ->
            OutlinedTextField(
                value = draft.caseStudyMarkdown,
                onValueChange = { updateDraft(draft.copy(caseStudyMarkdown = it)) },
                modifier = paneMod.heightIn(min = 300.dp),
                label = { Text("Case Study Markdown *") },
            )
        }
        val previewPane: @Composable (Modifier) -> Unit = { paneMod ->
            EditorialCard(modifier = paneMod.heightIn(min = 300.dp)) {
                Text(
                    text = "Live Case Study Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                PortfolioMarkdownView(
                    markdown = draft.caseStudyMarkdown.ifBlank { "_Write case study Markdown to preview._" },
                    platformActions = platformActions,
                )
            }
        }

        when {
            showPreview && isWide -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                editorPane(Modifier.weight(1f))
                previewPane(Modifier.weight(1f))
            }
            showPreview -> {
                editorPane(Modifier.fillMaxWidth())
                previewPane(Modifier.fillMaxWidth())
            }
            else -> editorPane(Modifier.fillMaxWidth())
        }
    }
}
