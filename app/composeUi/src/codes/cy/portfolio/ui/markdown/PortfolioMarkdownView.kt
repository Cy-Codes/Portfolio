package codes.cy.portfolio.ui.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import codes.cy.portfolio.ui.demos.DemoRegistry
import codes.cy.portfolio.ui.platform.NoOpPlatformActions
import codes.cy.portfolio.ui.platform.PlatformActions
import codes.cy.portfolio.ui.theme.PortfolioThemeDefaults
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

/**
 * Renders rich Markdown content with `Highlights` code blocks and live interactive
 * `demo:<id>` Compose Multiplatform demo islands.
 */
@Composable
fun PortfolioMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    platformActions: PlatformActions = NoOpPlatformActions,
) {
    val segments = remember(markdown) { MarkdownSegmentParser.parse(markdown) }
    val isDark = PortfolioThemeDefaults.isDark
    val editorialColors = PortfolioThemeDefaults.colors
    val uriHandler = remember(platformActions) {
        object : UriHandler {
            override fun openUri(uri: String) = platformActions.openExternalUrl(uri)
        }
    }

    val highlightsBuilder = remember(isDark) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = isDark))
    }

    val mdColors = markdownColor(
        text = MaterialTheme.colorScheme.onBackground,
        codeBackground = editorialColors.codeContainer,
        inlineCodeBackground = editorialColors.codeContainer,
        dividerColor = editorialColors.hairlineBorder,
        tableBackground = MaterialTheme.colorScheme.surfaceVariant,
        darkTheme = isDark,
    )

    val mdTypography = markdownTypography(
        h1 = MaterialTheme.typography.headlineMedium,
        h2 = MaterialTheme.typography.headlineSmall,
        h3 = MaterialTheme.typography.titleLarge,
        h4 = MaterialTheme.typography.titleMedium,
        paragraph = MaterialTheme.typography.bodyLarge,
        text = MaterialTheme.typography.bodyLarge,
        quote = MaterialTheme.typography.bodyLarge.copy(color = editorialColors.mutedText),
        code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        inlineCode = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        textLink = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        ),
    )

    val mdComponents = remember(highlightsBuilder) {
        markdownComponents(
            codeFence = { model ->
                MarkdownHighlightedCodeFence(
                    content = model.content,
                    node = model.node,
                    style = model.typography.code,
                    highlightsBuilder = highlightsBuilder,
                    showHeader = true,
                )
            },
            codeBlock = { model ->
                MarkdownHighlightedCodeBlock(
                    content = model.content,
                    node = model.node,
                    style = model.typography.code,
                    highlightsBuilder = highlightsBuilder,
                )
            },
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Prose -> {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        Markdown(
                            content = segment.markdown,
                            colors = mdColors,
                            typography = mdTypography,
                            components = mdComponents,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is MarkdownSegment.DemoIsland -> {
                    DemoRegistry.DemoIslandCard(
                        demoId = segment.demoId,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
