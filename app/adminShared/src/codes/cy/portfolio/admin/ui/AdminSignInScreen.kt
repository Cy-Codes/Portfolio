package codes.cy.portfolio.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.admin.data.AdminAuthMode
import codes.cy.portfolio.admin.features.auth.AdminAuthIntent
import codes.cy.portfolio.admin.features.auth.AdminAuthState
import codes.cy.portfolio.ui.components.EditorialCard
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults

private const val MAX_SIGN_IN_CARD_WIDTH_DP: Int = 560

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSignInScreen(
    state: AdminAuthState,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onIntent: (AdminAuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealToken by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        EditorialCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_SIGN_IN_CARD_WIDTH_DP.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AdminIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "Portfolio Admin Console",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Protected Admin CMS & Storage Control Plane",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDark) PortfolioIcons.Sun else PortfolioIcons.Moon,
                        contentDescription = "Toggle color mode",
                    )
                }
            }

            HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

            AdminFeedbackBanner(
                errorMessage = state.errorMessage,
                statusMessage = null,
                onDismiss = { onIntent(AdminAuthIntent.ClearError) },
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { onIntent(AdminAuthIntent.BaseUrlChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Admin Server Base URL") },
                placeholder = { Text("http://127.0.0.1:8081") },
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Authentication Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AdminAuthMode.entries.forEach { authMode ->
                        FilterChip(
                            selected = state.mode == authMode,
                            onClick = { onIntent(AdminAuthIntent.ModeChanged(authMode)) },
                            label = { Text(authMode.label) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.tokenInput,
                onValueChange = { onIntent(AdminAuthIntent.TokenChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    val fieldLabel = when (state.mode) {
                        AdminAuthMode.DEV_BEARER_TOKEN -> "Bearer JWT (from ./scripts/dev-token.sh owner)"
                        AdminAuthMode.OAUTH_SESSION_TOKEN -> "OAuth Owner Session JWT"
                        AdminAuthMode.OIDC_ID_TOKEN -> "Google / OIDC Bearer ID Token"
                    }
                    Text(fieldLabel)
                },
                visualTransformation = if (revealToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealToken = !revealToken }) {
                        Icon(
                            imageVector = AdminIcons.Eye,
                            contentDescription = if (revealToken) "Hide token" else "Show token",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIntent(AdminAuthIntent.SignIn) }),
            )

            if (state.mode == AdminAuthMode.DEV_BEARER_TOKEN) {
                MetadataMonoTag(
                    label = "Local dev token CLI: ./scripts/dev-token.sh owner",
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { onIntent(AdminAuthIntent.SignIn) },
                enabled = !state.isAuthenticating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isAuthenticating) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Verifying /api/v1/admin/auth/session...")
                    }
                } else {
                    Text("Verify Session & Enter Workspace")
                }
            }
        }
    }
}
