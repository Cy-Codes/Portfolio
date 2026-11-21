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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.presentation.features.profile.ProfileIntent
import codes.cy.portfolio.presentation.features.profile.ProfileState
import codes.cy.portfolio.presentation.navigation.ProfileComponent
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.PlatformVerticalScrollbar
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.EmptyStateView
import codes.cy.portfolio.ui.components.ErrorStateView
import codes.cy.portfolio.ui.components.LoadingStateView
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.components.SectionHeader
import codes.cy.portfolio.ui.markdown.PortfolioMarkdownView
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.platform.formatMonthYear
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.arkivanov.decompose.extensions.compose.subscribeAsState

private const val MAX_PROFILE_CONTAINER_WIDTH_DP: Int = 1040

@Composable
fun ProfileScreen(
    component: ProfileComponent,
    adaptiveSpec: AdaptiveLayoutSpec,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

    when (val current = state) {
        is ProfileState.Loading -> LoadingStateView(modifier = modifier.fillMaxSize())
        is ProfileState.Error -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = adaptiveSpec.contentHorizontalPaddingDp.dp, vertical = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            ErrorStateView(
                message = current.displayMessage,
                onRetry = { component.onIntent(ProfileIntent.Refresh) },
                modifier = Modifier.widthIn(max = MAX_PROFILE_CONTAINER_WIDTH_DP.dp),
            )
        }

        is ProfileState.Content -> ProfileContentBody(
            state = current,
            adaptiveSpec = adaptiveSpec,
            onIntent = component::onIntent,
            platformActions = platformActions,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfileContentBody(
    state: ProfileState.Content,
    adaptiveSpec: AdaptiveLayoutSpec,
    onIntent: (ProfileIntent) -> Unit,
    platformActions: PlatformActions,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val overview = state.overview

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
                    .widthIn(max = MAX_PROFILE_CONTAINER_WIDTH_DP.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                HeroProfileCard(
                    overview = overview,
                    isRefreshing = state.isRefreshing,
                    onIntent = onIntent,
                    platformActions = platformActions,
                )

                SkillsSection(
                    state = state,
                    onIntent = onIntent,
                )

                ExperienceSection(experiences = overview.experiences)

                CredentialsSection(
                    education = overview.education,
                    certifications = overview.certifications,
                    gridColumns = adaptiveSpec.gridColumns,
                    onOpenCredentialUrl = { url -> platformActions.openExternalUrl(url) },
                )
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
private fun HeroProfileCard(
    overview: PortfolioOverviewDto,
    isRefreshing: Boolean,
    onIntent: (ProfileIntent) -> Unit,
    platformActions: PlatformActions,
) {
    val profile = overview.profile
    val editorialColors = PortfolioThemeDefaults.colors

    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = profile?.fullName ?: "Engineering Portfolio",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = profile?.headline ?: "Systems, Mobile & Backend Architecture",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(
                onClick = { onIntent(ProfileIntent.Refresh) },
                enabled = !isRefreshing,
            ) {
                Icon(
                    imageVector = PortfolioIcons.Refresh,
                    contentDescription = "Refresh profile",
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profile?.location?.takeIf { it.isNotBlank() }?.let { location ->
                MetadataMonoTag(label = location)
            }
            profile?.availability?.takeIf { it.isNotBlank() }?.let { availability ->
                MetadataMonoTag(
                    label = availability,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (isRefreshing) {
                MetadataMonoTag(
                    label = "Refreshing...",
                    containerColor = editorialColors.accentSurface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (profile != null && profile.bioMarkdown.isNotBlank()) {
            PortfolioMarkdownView(
                markdown = profile.bioMarkdown,
                platformActions = platformActions,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Button(onClick = { onIntent(ProfileIntent.OpenProjects) }) {
                Text("Explore Projects")
            }
            OutlinedButton(onClick = { onIntent(ProfileIntent.OpenWriteups) }) {
                Text("Read Writeups")
            }
            OutlinedButton(onClick = { onIntent(ProfileIntent.OpenResumeDocs) }) {
                Text("Resume & Docs")
            }
            profile?.socialLinks.orEmpty().forEach { social ->
                OutlinedButton(onClick = { onIntent(ProfileIntent.ClickSocialLink(social.url)) }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(social.label)
                        Icon(
                            imageVector = PortfolioIcons.ExternalLink,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsSection(
    state: ProfileState.Content,
    onIntent: (ProfileIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Core Capabilities & Stack",
            subtitle = "Filter highlighted technologies across mobile, WebAssembly, backend, and cloud.",
        )

        if (state.overview.featuredSkills.isEmpty()) {
            EmptyStateView(
                title = "No featured skills published yet",
                subtitle = "Skills appear here once configured in the portfolio backend.",
            )
            return
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.selectedSkillCategory == null,
                onClick = { onIntent(ProfileIntent.SelectSkillCategory(null)) },
                label = { Text("All") },
            )
            state.skillCategories.forEach { category ->
                FilterChip(
                    selected = state.selectedSkillCategory == category,
                    onClick = { onIntent(ProfileIntent.SelectSkillCategory(category)) },
                    label = { Text(category) },
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.filteredSkills.forEach { skill ->
                MetadataMonoTag(
                    label = "${skill.name} · ${skill.category}",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ExperienceSection(experiences: List<ExperienceDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Experience",
            subtitle = "Recent architecture and engineering leadership roles.",
        )
        if (experiences.isEmpty()) {
            EmptyStateView(
                title = "No experience entries yet",
                subtitle = "Career timeline entries will populate here automatically.",
            )
            return
        }
        experiences.forEach { exp ->
            ExperienceCard(exp = exp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperienceCard(exp: ExperienceDto) {
    val dateRange = buildString {
        append(exp.startDate.formatMonthYear())
        append(" — ")
        append(if (exp.isCurrent) "Present" else exp.endDate?.formatMonthYear() ?: "Present")
    }

    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = exp.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(exp.company, exp.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            MetadataMonoTag(label = dateRange)
        }

        exp.highlights.forEach { bullet ->
            Text(
                text = "• $bullet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (exp.skills.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                exp.skills.forEach { skill ->
                    MetadataMonoTag(label = skill.name)
                }
            }
        }
    }
}

@Composable
private fun CredentialsSection(
    education: List<EducationDto>,
    certifications: List<CertificationDto>,
    gridColumns: Int,
    onOpenCredentialUrl: (String) -> Unit,
) {
    if (education.isEmpty() && certifications.isEmpty()) return

    val sideBySide = gridColumns >= 2 && education.isNotEmpty() && certifications.isNotEmpty()
    if (sideBySide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            EducationColumn(education = education, modifier = Modifier.weight(1f))
            CertificationsColumn(
                certifications = certifications,
                onOpenCredentialUrl = onOpenCredentialUrl,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (education.isNotEmpty()) {
                EducationColumn(education = education, modifier = Modifier.fillMaxWidth())
            }
            if (certifications.isNotEmpty()) {
                CertificationsColumn(
                    certifications = certifications,
                    onOpenCredentialUrl = onOpenCredentialUrl,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EducationColumn(
    education: List<EducationDto>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(title = "Education")
        education.forEach { edu ->
            val range = "${edu.startDate.formatMonthYear()} — ${edu.endDate?.formatMonthYear() ?: "Present"}"
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = edu.degree, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(edu.institution, edu.fieldOfStudy).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MetadataMonoTag(label = range)
            }
        }
    }
}

@Composable
private fun CertificationsColumn(
    certifications: List<CertificationDto>,
    onOpenCredentialUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(title = "Certifications")
        certifications.forEach { cert ->
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = cert.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = cert.issuer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetadataMonoTag(label = "Issued ${cert.issuedDate.formatMonthYear()}")
                    cert.credentialUrl?.let { url ->
                        OutlinedButton(onClick = { onOpenCredentialUrl(url) }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Verify")
                                Icon(PortfolioIcons.ExternalLink, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
