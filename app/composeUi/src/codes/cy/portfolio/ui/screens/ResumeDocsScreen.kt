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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsIntent
import codes.cy.portfolio.presentation.features.resumedocs.ResumeDocsState
import codes.cy.portfolio.presentation.navigation.ResumeDocsComponent
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.EmptyStateView
import codes.cy.portfolio.ui.components.ErrorStateView
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.platform.displayLabel
import codes.cy.portfolio.ui.platform.formatByteSize
import codes.cy.portfolio.ui.platform.formatShortDate
import codes.cy.portfolio.ui.theme.PortfolioIcons
import com.arkivanov.decompose.extensions.compose.subscribeAsState

private const val MAX_DOCS_CONTAINER_WIDTH_DP: Int = 1120
private const val SHORT_SHA_PREFIX_LEN: Int = 12

@Composable
fun ResumeDocsScreen(
    component: ResumeDocsComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

    when (val current = state) {
        is ResumeDocsState.Loading -> LoadingStateView(
            message = "Loading resumes & published documents...",
            modifier = modifier.fillMaxSize(),
        )

        is ResumeDocsState.Error -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = adaptiveSpec.contentHorizontalPaddingDp.dp, vertical = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            ErrorStateView(
                message = current.displayMessage,
                onRetry = { component.onIntent(ResumeDocsIntent.Load) },
                modifier = Modifier.widthIn(max = MAX_DOCS_CONTAINER_WIDTH_DP.dp),
            )
        }

        is ResumeDocsState.Content -> ResumeDocsContentBody(
            state = current,
            adaptiveSpec = adaptiveSpec,
            onIntent = component::onIntent,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResumeDocsContentBody(
    state: ResumeDocsState.Content,
    adaptiveSpec: AdaptiveLayoutSpec,
    onIntent: (ResumeDocsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val filteredDocs = state.filteredDocuments
    val columnCount = adaptiveSpec.gridColumns.coerceAtLeast(1)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = adaptiveSpec.contentHorizontalPaddingDp.dp, vertical = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = MAX_DOCS_CONTAINER_WIDTH_DP.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "Resume & Public Documents",
                        subtitle = "Verified PDFs, architecture slides, and technical papers delivered via pure GET redirects.",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onIntent(ResumeDocsIntent.Refresh) },
                        enabled = !state.isRefreshing,
                    ) {
                        Icon(PortfolioIcons.Refresh, contentDescription = "Refresh documents")
                    }
                }

                if (state.availableCategories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedCategory == null,
                            onClick = { onIntent(ResumeDocsIntent.SelectCategory(null)) },
                            label = { Text("All") },
                        )
                        state.availableCategories.forEach { category ->
                            FilterChip(
                                selected = state.selectedCategory == category,
                                onClick = { onIntent(ResumeDocsIntent.SelectCategory(category)) },
                                label = { Text(category.displayLabel()) },
                            )
                        }
                    }
                }

                if (filteredDocs.isEmpty()) {
                    EmptyStateView(
                        title = "No public documents in this category",
                        subtitle = "Select another category or reset the filter to view all documents.",
                        actionLabel = if (state.selectedCategory != null) "Show All" else null,
                        onAction = if (state.selectedCategory != null) {
                            { onIntent(ResumeDocsIntent.SelectCategory(null)) }
                        } else {
                            null
                        },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        filteredDocs.chunked(columnCount).forEach { rowDocs ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                rowDocs.forEach { doc ->
                                    DocumentCard(
                                        document = doc,
                                        onDownload = { onIntent(ResumeDocsIntent.RequestDownload(doc)) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columnCount - rowDocs.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentCard(
    document: DocumentDto,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorialCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            MetadataMonoTag(
                label = document.category.displayLabel(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Text(
            text = document.originalFilename,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetadataMonoTag(label = document.sizeBytes.formatByteSize())
            MetadataMonoTag(label = document.mimeType)
            MetadataMonoTag(label = "SHA256 ${document.sha256.take(SHORT_SHA_PREFIX_LEN)}")
            MetadataMonoTag(label = "Added ${document.createdAt.formatShortDate()}")
        }

        Button(
            onClick = onDownload,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PortfolioIcons.Download, contentDescription = null)
                Text("Download")
            }
        }
    }
}
