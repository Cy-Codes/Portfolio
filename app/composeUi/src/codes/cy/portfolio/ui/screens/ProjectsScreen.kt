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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.presentation.features.projects.ProjectsIntent
import codes.cy.portfolio.presentation.features.projects.ProjectsState
import codes.cy.portfolio.presentation.navigation.ProjectsPanelsComponent
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
import codes.cy.portfolio.ui.platform.displayLabel
import codes.cy.portfolio.ui.platform.formatShortDate
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.panels.ChildPanels
import com.arkivanov.decompose.extensions.compose.experimental.panels.HorizontalChildPanelsLayout
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.panels.ChildPanelsMode

private const val LIST_PANEL_WEIGHT: Float = 0.38f
private const val DETAIL_PANEL_WEIGHT: Float = 0.62f

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun ProjectsScreen(
    component: ProjectsPanelsComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(adaptiveSpec.projectsPanelMode) {
        component.setMode(adaptiveSpec.projectsPanelMode)
    }

    ChildPanels(
        panels = component.panels,
        mainChild = { child ->
            val childState by child.instance.state.subscribeAsState()
            ProjectsListPane(
                state = childState,
                onIntent = child.instance.onIntent,
                horizontalPaddingDp = adaptiveSpec.contentHorizontalPaddingDp,
                modifier = Modifier.fillMaxSize(),
            )
        },
        detailsChild = { child ->
            val childState by child.instance.state.subscribeAsState()
            ProjectDetailPane(
                state = childState,
                showBackButton = adaptiveSpec.projectsPanelMode == ChildPanelsMode.SINGLE,
                maxReadingMeasureDp = adaptiveSpec.maxReadingMeasureDp,
                onIntent = child.instance.onIntent,
                platformActions = platformActions,
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
                    title = "Select a Project",
                    subtitle = "Choose any project on the left to inspect its architecture case study.",
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }
        },
        layout = HorizontalChildPanelsLayout(
            dualWeights = LIST_PANEL_WEIGHT to DETAIL_PANEL_WEIGHT,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectsListPane(
    state: ProjectsState,
    onIntent: (ProjectsIntent) -> Unit,
    horizontalPaddingDp: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val availableSkills = state.items
        .flatMap { project -> project.skills.map { it.name } }
        .distinct()
        .sorted()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPaddingDp.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "projects-header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(
                            title = "Projects",
                            subtitle = "Production architectures, libraries, and case studies.",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onIntent(ProjectsIntent.Refresh) }) {
                            Icon(PortfolioIcons.Refresh, contentDescription = "Refresh projects")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = state.featuredOnly,
                            onClick = { onIntent(ProjectsIntent.ToggleFeaturedOnly(!state.featuredOnly)) },
                            label = { Text("Featured Only") },
                        )
                        if (state.selectedSkill != null) {
                            FilterChip(
                                selected = true,
                                onClick = { onIntent(ProjectsIntent.FilterBySkill(null)) },
                                label = { Text("Skill: ${state.selectedSkill} ×") },
                            )
                        } else {
                            availableSkills.forEach { skill ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onIntent(ProjectsIntent.FilterBySkill(skill)) },
                                    label = { Text(skill) },
                                )
                            }
                        }
                    }
                }
            }

            val errMessage = state.displayErrorMessage
            if (errMessage != null && state.items.isEmpty()) {
                item(key = "projects-error") {
                    ErrorStateView(
                        message = errMessage,
                        onRetry = { onIntent(ProjectsIntent.LoadInitial) },
                    )
                }
            } else if (state.isLoadingList && state.items.isEmpty()) {
                item(key = "projects-loading") {
                    LoadingStateView(message = "Loading projects...")
                }
            } else if (state.items.isEmpty()) {
                item(key = "projects-empty") {
                    EmptyStateView(
                        title = "No matching projects",
                        subtitle = "Clear active filters to browse all portfolio projects.",
                        actionLabel = "Reset Filters",
                        onAction = {
                            onIntent(ProjectsIntent.ToggleFeaturedOnly(false))
                            onIntent(ProjectsIntent.FilterBySkill(null))
                        },
                    )
                }
            } else {
                items(
                    items = state.items,
                    key = { it.id },
                ) { project ->
                    ProjectSummaryCard(
                        project = project,
                        selected = project.slug == state.selectedSlug,
                        onClick = { onIntent(ProjectsIntent.SelectProject(project.slug)) },
                    )
                }

                if (state.hasNextPage) {
                    item(key = "projects-load-more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            OutlinedButton(
                                onClick = { onIntent(ProjectsIntent.LoadNextPage) },
                                enabled = !state.isLoadingList,
                            ) {
                                Text(if (state.isLoadingList) "Loading page..." else "Load More Projects")
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
private fun ProjectSummaryCard(
    project: ProjectSummaryDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val editorialColors = PortfolioThemeDefaults.colors
    val statusColor = when (project.status) {
        ProjectStatus.ACTIVE -> editorialColors.statusActive
        ProjectStatus.PROTOTYPE -> editorialColors.statusPrototype
        ProjectStatus.ARCHIVED -> editorialColors.statusArchived
    }

    EditorialCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            MetadataMonoTag(
                label = project.status.displayLabel(),
                contentColor = statusColor,
            )
        }
        Text(
            text = project.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (project.isFeatured) {
                MetadataMonoTag(
                    label = "Featured",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            project.skills.forEach { skill ->
                MetadataMonoTag(label = skill.name)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectDetailPane(
    state: ProjectsState,
    showBackButton: Boolean,
    maxReadingMeasureDp: Int,
    onIntent: (ProjectsIntent) -> Unit,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val detail = state.selectedProject

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
                if (showBackButton) {
                    TextButton(onClick = { onIntent(ProjectsIntent.ClearSelection) }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PortfolioIcons.ArrowBack, contentDescription = null)
                            Text("All Projects")
                        }
                    }
                }

                when {
                    state.isLoadingDetail && detail == null -> LoadingStateView(message = "Loading case study...")
                    detail == null -> {
                        val message = state.displayErrorMessage ?: "Select a project to inspect full details."
                        ErrorStateView(
                            message = message,
                            onRetry = {
                                state.selectedSlug?.let { slug -> onIntent(ProjectsIntent.SelectProject(slug)) }
                            },
                        )
                    }

                    else -> {
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
                                    text = detail.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                MetadataMonoTag(
                                    label = "${detail.status.displayLabel()} · Updated ${detail.updatedAt.formatShortDate()}",
                                )
                            }
                            if (state.isLoadingDetail) {
                                CircularProgressIndicator()
                            }
                        }

                        Text(
                            text = detail.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            detail.skills.forEach { skill ->
                                MetadataMonoTag(label = skill.name)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            detail.liveUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                Button(onClick = { onIntent(ProjectsIntent.OpenExternalLink(url)) }) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Live Demo")
                                        Icon(PortfolioIcons.ExternalLink, contentDescription = null)
                                    }
                                }
                            }
                            detail.repoUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                OutlinedButton(onClick = { onIntent(ProjectsIntent.OpenExternalLink(url)) }) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Source Repository")
                                        Icon(PortfolioIcons.ExternalLink, contentDescription = null)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

                        PortfolioMarkdownView(
                            markdown = detail.caseStudyMarkdown,
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
