package codes.cy.portfolio.presentation.features.readarticle

data class TocHeading(
    val level: Int,
    val title: String,
    val anchorId: String,
)

data class MarkdownOutline(
    val headings: List<TocHeading>,
    val embeddedDemoIds: List<String>,
)

/**
 * Pure Markdown parser extracting table-of-contents headings and `demo:<id>` fenced blocks
 * so both Compose (Phase 4) and native SwiftUI + Compose Demo Islands (Phase 5) render identical metadata.
 */
object MarkdownOutlineExtractor {
    private const val CODE_FENCE_PREFIX: String = "```"
    private const val DEMO_PREFIX: String = "demo:"
    private const val MAX_TOC_LEVEL: Int = 4
    private val NON_ALNUM_REGEX: Regex = Regex("[^a-z0-9\\s-]")
    private val WHITESPACE_REGEX: Regex = Regex("\\s+")

    fun extract(markdown: String): MarkdownOutline {
        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split('\n')
        val headings = mutableListOf<TocHeading>()
        val demos = mutableListOf<String>()
        var inCodeFence = false

        // Single sequential fence-state scan distinguishes Markdown headings from `#` comments inside code blocks.
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith(CODE_FENCE_PREFIX)) {
                val info = trimmed.removePrefix(CODE_FENCE_PREFIX).trim()
                if (!inCodeFence && info.startsWith(DEMO_PREFIX)) {
                    val demoId = info.removePrefix(DEMO_PREFIX).trim()
                    if (demoId.isNotEmpty()) {
                        demos.add(demoId)
                    }
                }
                inCodeFence = !inCodeFence
                continue
            }
            if (inCodeFence) {
                continue
            }
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..MAX_TOC_LEVEL && trimmed.getOrNull(level) == ' ') {
                val title = trimmed.drop(level).trim()
                if (title.isNotEmpty()) {
                    headings.add(
                        TocHeading(
                            level = level,
                            title = title,
                            anchorId = toAnchorSlug(title),
                        ),
                    )
                }
            }
        }

        return MarkdownOutline(
            headings = headings.toList(),
            embeddedDemoIds = demos.distinct(),
        )
    }

    fun toAnchorSlug(title: String): String =
        title.trim()
            .lowercase()
            .replace(NON_ALNUM_REGEX, "")
            .replace(WHITESPACE_REGEX, "-")
            .trim('-')
            .ifEmpty { "section" }
}
