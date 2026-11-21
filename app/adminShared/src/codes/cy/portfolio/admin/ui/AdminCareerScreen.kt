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
import codes.cy.portfolio.admin.features.career.AdminCareerIntent
import codes.cy.portfolio.admin.features.career.AdminCareerState
import codes.cy.portfolio.admin.features.career.CareerSectionTab
import codes.cy.portfolio.admin.features.career.CertificationFormDraft
import codes.cy.portfolio.admin.features.career.EducationFormDraft
import codes.cy.portfolio.admin.features.career.ExperienceFormDraft
import codes.cy.portfolio.admin.features.career.ProfileFormDraft
import codes.cy.portfolio.admin.features.career.SkillFormDraft
import codes.cy.portfolio.admin.navigation.AdminCareerComponent
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.markdown.PortfolioMarkdownView
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.platform.formatMonthYear
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminCareerScreen(
    component: AdminCareerComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    platformActions: PlatformActions,
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
                    title = "Profile & Career CMS",
                    subtitle = "Manage bio, experience timeline, education, certifications, and skills.",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { component.onIntent(AdminCareerIntent.Load) }) {
                    Icon(PortfolioIcons.Refresh, contentDescription = "Reload career data")
                }
            }

            AdminFeedbackBanner(
                errorMessage = state.errorMessage,
                statusMessage = state.statusMessage,
                onDismiss = { component.onIntent(AdminCareerIntent.DismissFeedback) },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CareerSectionTab.entries.forEach { tab ->
                    FilterChip(
                        selected = state.activeTab == tab,
                        onClick = { component.onIntent(AdminCareerIntent.SelectTab(tab)) },
                        label = { Text(tab.label) },
                    )
                }
            }

            if (state.isLoading) {
                LoadingStateView(message = "Loading career records...")
            } else {
                when (state.activeTab) {
                    CareerSectionTab.PROFILE -> ProfileEditorSection(
                        state = state,
                        isWide = adaptiveSpec.gridColumns >= 2,
                        platformActions = platformActions,
                        onIntent = component::onIntent,
                    )

                    CareerSectionTab.EXPERIENCES -> ExperiencesSection(
                        state = state,
                        onIntent = component::onIntent,
                    )

                    CareerSectionTab.EDUCATION -> EducationSection(
                        state = state,
                        onIntent = component::onIntent,
                    )

                    CareerSectionTab.CERTIFICATIONS -> CertificationsSection(
                        state = state,
                        onIntent = component::onIntent,
                    )

                    CareerSectionTab.SKILLS -> SkillsSection(
                        state = state,
                        onIntent = component::onIntent,
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

@Composable
private fun ProfileEditorSection(
    state: AdminCareerState,
    isWide: Boolean,
    platformActions: PlatformActions,
    onIntent: (AdminCareerIntent) -> Unit,
) {
    val draft = state.profileDraft
    var newLinkLabel by remember { mutableStateOf("") }
    var newLinkUrl by remember { mutableStateOf("") }

    val updateDraft: (ProfileFormDraft) -> Unit = { next ->
        onIntent(AdminCareerIntent.UpdateProfileDraft(next))
    }

    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Owner Profile", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { onIntent(AdminCareerIntent.SaveProfile) },
                enabled = !state.isSaving,
            ) {
                Text(if (state.isSaving) "Saving..." else "Save Profile")
            }
        }

        OutlinedTextField(
            value = draft.fullName,
            onValueChange = { updateDraft(draft.copy(fullName = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Full Name *") },
        )
        OutlinedTextField(
            value = draft.headline,
            onValueChange = { updateDraft(draft.copy(headline = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Headline *") },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.location,
                onValueChange = { updateDraft(draft.copy(location = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Location") },
            )
            OutlinedTextField(
                value = draft.availability,
                onValueChange = { updateDraft(draft.copy(availability = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Availability") },
            )
        }

        DocumentAttachmentSelector(
            label = "Resume Document",
            documents = state.documents,
            selectedDocumentId = draft.resumeDocumentId,
            onSelect = { docId -> updateDraft(draft.copy(resumeDocumentId = docId)) },
        )

        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

        Text("Social Links", style = MaterialTheme.typography.titleMedium)
        draft.socialLinks.forEachIndexed { idx, link ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${link.label}: ${link.url}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onIntent(AdminCareerIntent.RemoveSocialLink(idx)) }) {
                    Icon(AdminIcons.Trash, contentDescription = "Remove link")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newLinkLabel,
                onValueChange = { newLinkLabel = it },
                modifier = Modifier.weight(0.35f),
                singleLine = true,
                label = { Text("Label (e.g. GitHub)") },
            )
            OutlinedTextField(
                value = newLinkUrl,
                onValueChange = { newLinkUrl = it },
                modifier = Modifier.weight(0.50f),
                singleLine = true,
                label = { Text("URL") },
            )
            OutlinedButton(
                onClick = {
                    onIntent(AdminCareerIntent.AddSocialLink(newLinkLabel, newLinkUrl))
                    if (newLinkLabel.isNotBlank() && newLinkUrl.isNotBlank()) {
                        newLinkLabel = ""
                        newLinkUrl = ""
                    }
                },
            ) {
                Text("Add")
            }
        }

        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

        Text("Bio Markdown & Live Preview", style = MaterialTheme.typography.titleMedium)
        val editorField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = draft.bioMarkdown,
                onValueChange = { updateDraft(draft.copy(bioMarkdown = it)) },
                modifier = fieldModifier.heightIn(min = 220.dp),
                label = { Text("Bio Markdown *") },
            )
        }
        val previewBox: @Composable (Modifier) -> Unit = { boxModifier ->
            EditorialCard(modifier = boxModifier) {
                Text(
                    text = "Live Bio Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                PortfolioMarkdownView(
                    markdown = draft.bioMarkdown.ifBlank { "_Nothing yet — type Markdown on the left._" },
                    platformActions = platformActions,
                )
            }
        }
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                editorField(Modifier.weight(1f))
                previewBox(Modifier.weight(1f))
            }
        } else {
            editorField(Modifier.fillMaxWidth())
            previewBox(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperiencesSection(
    state: AdminCareerState,
    onIntent: (AdminCareerIntent) -> Unit,
) {
    val draft = state.experienceDraft
    val updateDraft: (ExperienceFormDraft) -> Unit = { next ->
        onIntent(AdminCareerIntent.UpdateExperienceDraft(next))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (draft.editingId == null) "New Experience" else "Edit Experience",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onIntent(AdminCareerIntent.NewExperience) }) {
                        Text("Clear")
                    }
                    Button(
                        onClick = { onIntent(AdminCareerIntent.SaveExperience) },
                        enabled = !state.isSaving,
                    ) {
                        Text(if (draft.editingId == null) "Create" else "Update")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.company,
                    onValueChange = { updateDraft(draft.copy(company = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Company *") },
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { updateDraft(draft.copy(title = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Role Title *") },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.location,
                    onValueChange = { updateDraft(draft.copy(location = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Location") },
                )
                OutlinedTextField(
                    value = draft.sortOrderInput,
                    onValueChange = { updateDraft(draft.copy(sortOrderInput = it)) },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true,
                    label = { Text("Sort Order") },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft.startDateInput,
                    onValueChange = { updateDraft(draft.copy(startDateInput = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Start Date (YYYY-MM-DD) *") },
                )
                OutlinedTextField(
                    value = draft.endDateInput,
                    onValueChange = { updateDraft(draft.copy(endDateInput = it)) },
                    modifier = Modifier.weight(1f),
                    enabled = !draft.isCurrent,
                    singleLine = true,
                    label = { Text("End Date (YYYY-MM-DD)") },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Current", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = draft.isCurrent,
                        onCheckedChange = { checked -> updateDraft(draft.copy(isCurrent = checked)) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.highlightsInput,
                onValueChange = { updateDraft(draft.copy(highlightsInput = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                label = { Text("Highlights (one per line)") },
            )

            if (state.skills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.skills.forEach { skill ->
                        val selected = skill.id in draft.selectedSkillIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextIds = if (selected) {
                                    draft.selectedSkillIds - skill.id
                                } else {
                                    draft.selectedSkillIds + skill.id
                                }
                                updateDraft(draft.copy(selectedSkillIds = nextIds))
                            },
                            label = { Text(skill.name) },
                        )
                    }
                }
            }
        }

        state.experiences.forEach { exp ->
            EditorialCard(
                selected = draft.editingId == exp.id,
                onClick = { onIntent(AdminCareerIntent.EditExperience(exp)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${exp.title} · ${exp.company}", style = MaterialTheme.typography.titleMedium)
                        val period = buildString {
                            append(exp.startDate.formatMonthYear())
                            append(" – ")
                            append(if (exp.isCurrent) "Present" else exp.endDate?.formatMonthYear() ?: "")
                        }
                        Text(period, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onIntent(AdminCareerIntent.DeleteExperience(exp.id)) }) {
                        Icon(AdminIcons.Trash, contentDescription = "Delete experience")
                    }
                }
            }
        }
    }
}

@Composable
private fun EducationSection(
    state: AdminCareerState,
    onIntent: (AdminCareerIntent) -> Unit,
) {
    val draft = state.educationDraft
    val updateDraft: (EducationFormDraft) -> Unit = { next ->
        onIntent(AdminCareerIntent.UpdateEducationDraft(next))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (draft.editingId == null) "New Education Record" else "Edit Education Record",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onIntent(AdminCareerIntent.NewEducation) }) { Text("Clear") }
                    Button(
                        onClick = { onIntent(AdminCareerIntent.SaveEducation) },
                        enabled = !state.isSaving,
                    ) {
                        Text(if (draft.editingId == null) "Create" else "Update")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.institution,
                    onValueChange = { updateDraft(draft.copy(institution = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Institution *") },
                )
                OutlinedTextField(
                    value = draft.degree,
                    onValueChange = { updateDraft(draft.copy(degree = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Degree *") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.fieldOfStudy,
                    onValueChange = { updateDraft(draft.copy(fieldOfStudy = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Field of Study") },
                )
                OutlinedTextField(
                    value = draft.startDateInput,
                    onValueChange = { updateDraft(draft.copy(startDateInput = it)) },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                    label = { Text("Start (YYYY-MM-DD) *") },
                )
                OutlinedTextField(
                    value = draft.endDateInput,
                    onValueChange = { updateDraft(draft.copy(endDateInput = it)) },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                    label = { Text("End (YYYY-MM-DD)") },
                )
                OutlinedTextField(
                    value = draft.sortOrderInput,
                    onValueChange = { updateDraft(draft.copy(sortOrderInput = it)) },
                    modifier = Modifier.weight(0.4f),
                    singleLine = true,
                    label = { Text("Order") },
                )
            }
        }

        state.educationList.forEach { edu ->
            EditorialCard(
                selected = draft.editingId == edu.id,
                onClick = { onIntent(AdminCareerIntent.EditEducation(edu)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${edu.degree} — ${edu.institution}", style = MaterialTheme.typography.titleMedium)
                        val detail = listOfNotNull(edu.fieldOfStudy, "${edu.startDate.formatMonthYear()} – ${edu.endDate?.formatMonthYear() ?: "Present"}").joinToString(" · ")
                        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onIntent(AdminCareerIntent.DeleteEducation(edu.id)) }) {
                        Icon(AdminIcons.Trash, contentDescription = "Delete education")
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificationsSection(
    state: AdminCareerState,
    onIntent: (AdminCareerIntent) -> Unit,
) {
    val draft = state.certificationDraft
    val updateDraft: (CertificationFormDraft) -> Unit = { next ->
        onIntent(AdminCareerIntent.UpdateCertificationDraft(next))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (draft.editingId == null) "New Certification" else "Edit Certification",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onIntent(AdminCareerIntent.NewCertification) }) { Text("Clear") }
                    Button(
                        onClick = { onIntent(AdminCareerIntent.SaveCertification) },
                        enabled = !state.isSaving,
                    ) {
                        Text(if (draft.editingId == null) "Create" else "Update")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { updateDraft(draft.copy(name = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Certification Name *") },
                )
                OutlinedTextField(
                    value = draft.issuer,
                    onValueChange = { updateDraft(draft.copy(issuer = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Issuer *") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.issuedDateInput,
                    onValueChange = { updateDraft(draft.copy(issuedDateInput = it)) },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                    label = { Text("Issued (YYYY-MM-DD) *") },
                )
                OutlinedTextField(
                    value = draft.expiresDateInput,
                    onValueChange = { updateDraft(draft.copy(expiresDateInput = it)) },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                    label = { Text("Expires (YYYY-MM-DD)") },
                )
                OutlinedTextField(
                    value = draft.credentialUrl,
                    onValueChange = { updateDraft(draft.copy(credentialUrl = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Credential URL") },
                )
                OutlinedTextField(
                    value = draft.sortOrderInput,
                    onValueChange = { updateDraft(draft.copy(sortOrderInput = it)) },
                    modifier = Modifier.weight(0.4f),
                    singleLine = true,
                    label = { Text("Order") },
                )
            }
        }

        state.certifications.forEach { cert ->
            EditorialCard(
                selected = draft.editingId == cert.id,
                onClick = { onIntent(AdminCareerIntent.EditCertification(cert)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${cert.name} · ${cert.issuer}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Issued ${cert.issuedDate.formatMonthYear()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onIntent(AdminCareerIntent.DeleteCertification(cert.id)) }) {
                        Icon(AdminIcons.Trash, contentDescription = "Delete certification")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsSection(
    state: AdminCareerState,
    onIntent: (AdminCareerIntent) -> Unit,
) {
    val draft = state.skillDraft
    val updateDraft: (SkillFormDraft) -> Unit = { next ->
        onIntent(AdminCareerIntent.UpdateSkillDraft(next))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (draft.editingId == null) "New Skill" else "Edit Skill",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onIntent(AdminCareerIntent.NewSkill) }) { Text("Clear") }
                    Button(
                        onClick = { onIntent(AdminCareerIntent.SaveSkill) },
                        enabled = !state.isSaving,
                    ) {
                        Text(if (draft.editingId == null) "Create" else "Update")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { updateDraft(draft.copy(name = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Skill Name *") },
                )
                OutlinedTextField(
                    value = draft.category,
                    onValueChange = { updateDraft(draft.copy(category = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Category *") },
                )
                OutlinedTextField(
                    value = draft.sortOrderInput,
                    onValueChange = { updateDraft(draft.copy(sortOrderInput = it)) },
                    modifier = Modifier.weight(0.45f),
                    singleLine = true,
                    label = { Text("Order") },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Featured")
                    Switch(
                        checked = draft.isFeatured,
                        onCheckedChange = { checked -> updateDraft(draft.copy(isFeatured = checked)) },
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.skills.forEach { skill ->
                EditorialCard(
                    selected = draft.editingId == skill.id,
                    onClick = { onIntent(AdminCareerIntent.EditSkill(skill)) },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(skill.name, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MetadataMonoTag(label = skill.category)
                                if (skill.isFeatured) {
                                    MetadataMonoTag(
                                        label = "Featured",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { onIntent(AdminCareerIntent.DeleteSkill(skill.id)) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
