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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.admin.features.documents.AdminDocumentsIntent
import codes.cy.portfolio.admin.features.documents.AdminDocumentsState
import codes.cy.portfolio.admin.features.documents.UploadStage
import codes.cy.portfolio.admin.navigation.AdminDocumentsComponent
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.EmptyStateView
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.platform.displayLabel
import codes.cy.portfolio.ui.platform.formatByteSize
import codes.cy.portfolio.ui.platform.formatShortDate
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun AdminDocumentsScreen(
    component: AdminDocumentsComponent,
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
                    title = "Documents & Direct R2 / MinIO Upload",
                    subtitle = "SHA-256 checksum -> POST /presign -> direct binary PUT -> POST /confirm with magic-byte verification.",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { component.onIntent(AdminDocumentsIntent.Load) }) {
                    Icon(PortfolioIcons.Refresh, contentDescription = "Refresh documents")
                }
            }

            AdminFeedbackBanner(
                errorMessage = state.errorMessage,
                statusMessage = state.statusMessage,
                onDismiss = { component.onIntent(AdminDocumentsIntent.DismissFeedback) },
            )

            DirectUploadCard(
                state = state,
                onIntent = component::onIntent,
            )

            DocumentListCard(
                state = state,
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
private fun DirectUploadCard(
    state: AdminDocumentsState,
    onIntent: (AdminDocumentsIntent) -> Unit,
) {
    val uploading = state.uploadStage.isInProgress
    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Upload New Asset or PDF", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = { onIntent(AdminDocumentsIntent.PickFile) },
                enabled = !uploading,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AdminIcons.Upload, contentDescription = null)
                    Text("Choose File")
                }
            }
        }

        val file = state.selectedFile
        if (file != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetadataMonoTag(label = file.filename)
                MetadataMonoTag(label = file.mimeType)
                MetadataMonoTag(label = file.bytes.size.toLong().formatByteSize())
                state.computedSha256?.let { digest ->
                    MetadataMonoTag(label = "SHA-256 ${digest.take(12)}...")
                }
            }
        } else {
            Text(
                text = "Allowed formats: PDF, PNG, JPEG, WebP, Markdown (.md), Plain Text (.txt) up to 25 MB.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = state.titleInput,
            onValueChange = { onIntent(AdminDocumentsIntent.TitleChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uploading,
            label = { Text("Display Title *") },
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DocumentCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { if (!uploading) onIntent(AdminDocumentsIntent.CategoryChanged(category)) },
                    label = { Text(category.displayLabel()) },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Public", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.isPublic,
                    onCheckedChange = { checked -> if (!uploading) onIntent(AdminDocumentsIntent.IsPublicChanged(checked)) },
                )
            }
        }

        if (state.uploadStage != UploadStage.IDLE) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = state.uploadStage.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Button(
            onClick = { onIntent(AdminDocumentsIntent.UploadSelectedFile) },
            enabled = !uploading && state.selectedFile != null && state.titleInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uploading) state.uploadStage.label else "Presign, Upload Direct PUT & Confirm")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentListCard(
    state: AdminDocumentsState,
    onIntent: (AdminDocumentsIntent) -> Unit,
) {
    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Text("Uploaded Documents (${state.totalCount})", style = MaterialTheme.typography.titleLarge)

        when {
            state.isLoading && state.items.isEmpty() -> LoadingStateView(message = "Loading documents...")
            state.items.isEmpty() -> EmptyStateView(
                title = "No uploaded documents yet",
                subtitle = "Select a file above to upload directly to object storage.",
            )
            else -> {
                state.items.forEachIndexed { idx, doc ->
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
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(doc.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${doc.originalFilename} · ${doc.storageKey}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MetadataMonoTag(label = doc.category.displayLabel())
                                MetadataMonoTag(label = doc.mimeType)
                                MetadataMonoTag(label = doc.sizeBytes.formatByteSize())
                                MetadataMonoTag(
                                    label = if (doc.isPublic) "Public" else "Private",
                                    containerColor = if (doc.isPublic) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (doc.isPublic) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                MetadataMonoTag(label = "${doc.downloadCount} downloads")
                                MetadataMonoTag(label = doc.createdAt.formatShortDate())
                            }
                        }
                        IconButton(onClick = { onIntent(AdminDocumentsIntent.DeleteDocument(doc.id)) }) {
                            Icon(AdminIcons.Trash, contentDescription = "Delete document ${doc.title}")
                        }
                    }
                }

                AdminPaginationRow(
                    page = state.page,
                    totalPages = state.totalPages,
                    totalCount = state.totalCount,
                    isLoading = state.isLoading,
                    onPageChange = { onIntent(AdminDocumentsIntent.GoToPage(it)) },
                )
            }
        }
    }
}
