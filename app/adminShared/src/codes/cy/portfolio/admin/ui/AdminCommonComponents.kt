package codes.cy.portfolio.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.platform.displayLabel
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults

@Composable
fun AdminFeedbackBanner(
    errorMessage: String?,
    statusMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeMessage = errorMessage ?: statusMessage ?: return
    val isError = errorMessage != null
    val editorialColors = PortfolioThemeDefaults.colors

    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        editorialColors.accentSurface
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = activeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(PortfolioIcons.Clear, contentDescription = "Dismiss notification")
            }
        }
    }
}

@Composable
fun AdminPaginationRow(
    page: Int,
    totalPages: Int,
    totalCount: Long,
    isLoading: Boolean,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalCount == 0L && totalPages <= 1) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Page $page of ${totalPages.coerceAtLeast(1)} ($totalCount items)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onPageChange(page - 1) },
                enabled = !isLoading && page > 1,
            ) {
                Text("Prev")
            }
            OutlinedButton(
                onClick = { onPageChange(page + 1) },
                enabled = !isLoading && page < totalPages,
            ) {
                Text("Next")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentAttachmentSelector(
    label: String,
    documents: List<DocumentDto>,
    selectedDocumentId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        FilterChip(
            selected = selectedDocumentId == null,
            onClick = { onSelect(null) },
            label = { Text("None") },
        )
        documents.forEach { doc ->
            FilterChip(
                selected = selectedDocumentId == doc.id,
                onClick = { onSelect(if (selectedDocumentId == doc.id) null else doc.id) },
                label = { Text("${doc.title} (${doc.category.displayLabel()})") },
            )
        }
    }
}

@Composable
fun WriteupStatusBadge(status: WriteupStatus) {
    val editorialColors = PortfolioThemeDefaults.colors
    val tint = when (status) {
        WriteupStatus.PUBLISHED -> editorialColors.statusActive
        WriteupStatus.DRAFT -> editorialColors.statusPrototype
        WriteupStatus.ARCHIVED -> editorialColors.statusArchived
    }
    MetadataMonoTag(
        label = status.displayLabel(),
        containerColor = tint.copy(alpha = 0.14f),
        contentColor = tint,
    )
}

@Composable
fun ProjectStatusBadge(status: ProjectStatus) {
    val editorialColors = PortfolioThemeDefaults.colors
    val tint = when (status) {
        ProjectStatus.ACTIVE -> editorialColors.statusActive
        ProjectStatus.PROTOTYPE -> editorialColors.statusPrototype
        ProjectStatus.ARCHIVED -> editorialColors.statusArchived
    }
    MetadataMonoTag(
        label = status.displayLabel(),
        containerColor = tint.copy(alpha = 0.14f),
        contentColor = tint,
    )
}
