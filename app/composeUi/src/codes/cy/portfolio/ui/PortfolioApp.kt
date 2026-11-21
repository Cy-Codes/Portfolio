package codes.cy.portfolio.ui

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
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.presentation.navigation.RootComponent
import codes.cy.portfolio.presentation.navigation.TopLevelDestination
import codes.cy.portfolio.ui.adaptive.NavigationChrome
import codes.cy.portfolio.ui.adaptive.rememberAdaptiveLayoutSpec
import codes.cy.portfolio.ui.components.MetadataMonoTag
import codes.cy.portfolio.ui.platform.NoOpPlatformActions
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.screens.ProfileScreen
import codes.cy.portfolio.ui.screens.ProjectsScreen
import codes.cy.portfolio.ui.screens.ResumeDocsScreen
import codes.cy.portfolio.ui.screens.WriteupsScreen
import codes.cy.portfolio.ui.theme.PortfolioIcons
import codes.cy.portfolio.ui.theme.PortfolioTheme
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import codes.cy.portfolio.ui.theme.PortfolioThemeMode
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState

private const val DRAWER_WIDTH_DP: Int = 248

@Composable
fun PortfolioApp(
    rootComponent: RootComponent,
    platformActions: PlatformActions = NoOpPlatformActions,
    modifier: Modifier = Modifier,
) {
    var themeMode by remember { mutableStateOf(PortfolioThemeMode.SYSTEM) }

    PortfolioTheme(mode = themeMode) {
        val adaptiveSpec = rememberAdaptiveLayoutSpec()
        val activeDestination by rootComponent.activeDestination.subscribeAsState()
        val isDark = PortfolioThemeDefaults.isDark
        val toggleTheme: () -> Unit = { themeMode = themeMode.nextToggleMode(isDark) }

        LaunchedEffect(adaptiveSpec.viewportTier) {
            rootComponent.updateViewportTier(adaptiveSpec.viewportTier)
        }
        LaunchedEffect(rootComponent) {
            rootComponent.externalUrlEffects.collect { url ->
                platformActions.openExternalUrl(url)
            }
        }
        LaunchedEffect(rootComponent) {
            rootComponent.downloadEffects.collect { effect ->
                platformActions.triggerDownload(url = effect.url, filename = effect.filename)
            }
        }

        val contentBody: @Composable (Modifier) -> Unit = { innerModifier ->
            Children(
                stack = rootComponent.childStack,
                modifier = innerModifier.background(MaterialTheme.colorScheme.background),
                animation = stackAnimation(fade()),
            ) { created ->
                when (val child = created.instance) {
                    is RootComponent.Child.Profile -> ProfileScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        platformActions = platformActions,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is RootComponent.Child.Projects -> ProjectsScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        platformActions = platformActions,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is RootComponent.Child.Writeups -> WriteupsScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        platformActions = platformActions,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is RootComponent.Child.ResumeDocs -> ResumeDocsScreen(
                        component = child.component,
                        adaptiveSpec = adaptiveSpec,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

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
                            Text(
                                text = "CY · Portfolio",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            IconButton(onClick = toggleTheme) {
                                Icon(
                                    imageVector = if (isDark) PortfolioIcons.Sun else PortfolioIcons.Moon,
                                    contentDescription = "Toggle color mode",
                                )
                            }
                        }
                        HorizontalDivider(color = PortfolioThemeDefaults.colors.hairlineBorder)
                    }
                },
                bottomBar = {
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = activeDestination == destination,
                                onClick = { rootComponent.selectDestination(destination) },
                                icon = { Icon(destination.icon(), contentDescription = destination.shortLabel()) },
                                label = { Text(destination.shortLabel()) },
                            )
                        }
                    }
                },
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    contentBody(Modifier.fillMaxSize())
                }
            }

            NavigationChrome.NAVIGATION_RAIL -> Row(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavigationRail(
                    header = {
                        IconButton(onClick = toggleTheme) {
                            Icon(
                                imageVector = if (isDark) PortfolioIcons.Sun else PortfolioIcons.Moon,
                                contentDescription = "Toggle theme",
                            )
                        }
                    },
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = activeDestination == destination,
                            onClick = { rootComponent.selectDestination(destination) },
                            icon = { Icon(destination.icon(), contentDescription = destination.shortLabel()) },
                            label = { Text(destination.shortLabel()) },
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
                contentBody(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }

            NavigationChrome.PERMANENT_DRAWER -> PermanentNavigationDrawer(
                modifier = modifier.fillMaxSize(),
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(DRAWER_WIDTH_DP.dp),
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
                                    Text(
                                        text = "CY · Portfolio",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        text = "Systems & KMP",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = toggleTheme) {
                                    Icon(
                                        imageVector = if (isDark) PortfolioIcons.Sun else PortfolioIcons.Moon,
                                        contentDescription = "Toggle color mode",
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            TopLevelDestination.entries.forEach { destination ->
                                NavigationDrawerItem(
                                    selected = activeDestination == destination,
                                    onClick = { rootComponent.selectDestination(destination) },
                                    icon = { Icon(destination.icon(), contentDescription = null) },
                                    label = { Text(destination.drawerLabel()) },
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
                                Text(
                                    text = "Viewport",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MetadataMonoTag(label = adaptiveSpec.viewportTier.name)
                            }
                        }
                    }
                },
            ) {
                contentBody(Modifier.fillMaxSize())
            }
        }
    }
}

private fun TopLevelDestination.shortLabel(): String =
    when (this) {
        TopLevelDestination.PROFILE -> "Profile"
        TopLevelDestination.PROJECTS -> "Projects"
        TopLevelDestination.WRITEUPS -> "Writeups"
        TopLevelDestination.RESUME_DOCS -> "Docs"
    }

private fun TopLevelDestination.drawerLabel(): String =
    when (this) {
        TopLevelDestination.PROFILE -> "Profile & Career"
        TopLevelDestination.PROJECTS -> "Projects"
        TopLevelDestination.WRITEUPS -> "Engineering Writeups"
        TopLevelDestination.RESUME_DOCS -> "Resume & Documents"
    }

private fun TopLevelDestination.icon(): ImageVector =
    when (this) {
        TopLevelDestination.PROFILE -> PortfolioIcons.Profile
        TopLevelDestination.PROJECTS -> PortfolioIcons.Projects
        TopLevelDestination.WRITEUPS -> PortfolioIcons.Writeups
        TopLevelDestination.RESUME_DOCS -> PortfolioIcons.Resume
    }
