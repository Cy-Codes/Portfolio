package codes.cy.portfolio.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.admin.navigation.AdminRootComponent
import codes.cy.portfolio.admin.navigation.AdminSection
import codes.cy.portfolio.admin.ui.AdminCareerScreen
import codes.cy.portfolio.admin.ui.AdminDocumentsScreen
import codes.cy.portfolio.admin.ui.AdminIcons
import codes.cy.portfolio.admin.ui.AdminProjectsScreen
import codes.cy.portfolio.admin.ui.AdminSignInScreen
import codes.cy.portfolio.admin.ui.AdminStatsScreen
import codes.cy.portfolio.admin.ui.AdminWriteupsScreen
import codes.cy.portfolio.ui.adaptive.AdaptiveLayoutSpec
import codes.cy.portfolio.ui.adaptive.NavigationChrome
import codes.cy.portfolio.ui.adaptive.rememberAdaptiveLayoutSpec
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.platform.NoOpPlatformActions
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioTheme
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import codes.cy.portfolio.ui.theme.PortfolioThemeMode
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState

private const val ADMIN_DRAWER_WIDTH_DP: Int = 256

@Composable
fun AdminApp(
    rootComponent: AdminRootComponent,
    platformActions: PlatformActions = NoOpPlatformActions,
    modifier: Modifier = Modifier,
) {
    var themeMode by remember { mutableStateOf(PortfolioThemeMode.SYSTEM) }

    PortfolioTheme(mode = themeMode) {
        val adaptiveSpec = rememberAdaptiveLayoutSpec()
        val authState by rootComponent.authState.subscribeAsState()
        val activeSection by rootComponent.activeSection.subscribeAsState()
        val childStack by rootComponent.childStack.subscribeAsState()
        val isDark = PortfolioThemeDefaults.isDark
        val toggleTheme: () -> Unit = { themeMode = themeMode.nextToggleMode(isDark) }

        val activeInstance = childStack.active.instance
        if (activeInstance is AdminRootComponent.Child.SignIn || !authState.isAuthenticated) {
            AdminSignInScreen(
                state = authState,
                isDark = isDark,
                onToggleTheme = toggleTheme,
                onIntent = rootComponent::onAuthIntent,
                modifier = modifier.background(MaterialTheme.colorScheme.background),
            )
            return@PortfolioTheme
        }

        val workspaceContent: @Composable (Modifier) -> Unit = { innerMod ->
            Children(
                stack = rootComponent.childStack,
                modifier = innerMod.background(MaterialTheme.colorScheme.background),
                animation = stackAnimation(fade()),
            ) { created ->
                when (val child = created.instance) {
                    is AdminRootComponent.Child.SignIn -> Unit
                    is AdminRootComponent.Child.Stats -> AdminStatsScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AdminRootComponent.Child.Career -> AdminCareerScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        platformActions = platformActions,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AdminRootComponent.Child.Projects -> AdminProjectsScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        platformActions = platformActions,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AdminRootComponent.Child.Writeups -> AdminWriteupsScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        platformActions = platformActions,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AdminRootComponent.Child.Documents -> AdminDocumentsScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val sessionLabel = authState.session?.let { "${it.subject} (${it.authSource})" } ?: "Owner"

        when (adaptiveSpec.navigationChrome) {
            NavigationChrome.BOTTOM_BAR -> Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Portfolio Admin", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = sessionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row {
                                IconButton(onClick = toggleTheme) {
                                    Icon(
                                        imageVector = if (isDark) PortfolioIcons.Sun else PortfolioIcons.Moon,
                                        contentDescription = "Toggle theme",
                                    )
                                }
                                IconButton(onClick = rootComponent::signOut) {
                                    Icon(AdminIcons.Logout, contentDescription = "Sign out")
                                }
                            }
                        }
                        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)
                    }
                },
                bottomBar = {
                    NavigationBar {
                        AdminSection.entries.forEach { section ->
                            NavigationBarItem(
                                selected = activeSection == section,
                                onClick = { rootComponent.selectSection(section) },
                                icon = { Icon(section.icon(), contentDescription = section.shortLabel) },
                                label = { Text(section.shortLabel) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    workspaceContent(Modifier.fillMaxSize())
                }
            }

            NavigationChrome.NAVIGATION_RAIL -> Row(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavigationRail(
                    header = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = toggleTheme) {
                                Icon(
                                    imageVector = if (isDark) PortfolioIcons.Sun else PortfolioIcons.Moon,
                                    contentDescription = "Toggle theme",
                                )
                            }
                            IconButton(onClick = rootComponent::signOut) {
                                Icon(AdminIcons.Logout, contentDescription = "Sign out")
                            }
                        }
                    },
                ) {
                    AdminSection.entries.forEach { section ->
                        NavigationRailItem(
                            selected = activeSection == section,
                            onClick = { rootComponent.selectSection(section) },
                            icon = { Icon(section.icon(), contentDescription = section.shortLabel) },
                            label = { Text(section.shortLabel) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    MetadataMonoTag(
                        label = adaptiveSpec.viewportTier.name,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = PortfolioThemeDefaults.colors.hairlineBorder,
                )
                workspaceContent(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }

            NavigationChrome.PERMANENT_DRAWER -> PermanentNavigationDrawer(
                modifier = modifier.fillMaxSize(),
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(ADMIN_DRAWER_WIDTH_DP.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Portfolio Admin", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = sessionLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = toggleTheme) {
                                    Icon(
                                        imageVector = if (isDark) PortfolioIcons.Sun else PortfolioIcons.Moon,
                                        contentDescription = "Toggle theme",
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            AdminSection.entries.forEach { section ->
                                NavigationDrawerItem(
                                    selected = activeSection == section,
                                    onClick = { rootComponent.selectSection(section) },
                                    icon = { Icon(section.icon(), contentDescription = null) },
                                    label = { Text(section.drawerLabel) },
                                )
                            }

                            Spacer(Modifier.weight(1f))
                            HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, start = 6.dp, end = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MetadataMonoTag(label = adaptiveSpec.viewportTier.name)
                                OutlinedButton(onClick = rootComponent::signOut) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(AdminIcons.Logout, contentDescription = null)
                                        Text("Sign Out")
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                workspaceContent(Modifier.fillMaxSize())
            }
        }
    }
}

private fun AdminSection.icon(): ImageVector =
    when (this) {
        AdminSection.STATS -> AdminIcons.Stats
        AdminSection.CAREER -> PortfolioIcons.Profile
        AdminSection.PROJECTS -> PortfolioIcons.Projects
        AdminSection.WRITEUPS -> PortfolioIcons.Writeups
        AdminSection.DOCUMENTS -> PortfolioIcons.Resume
    }
