package codes.cy.portfolio.ui.markdown

sealed interface MarkdownSegment {
    data class Prose(val markdown: String) : MarkdownSegment
    data class DemoIsland(val demoId: String) : MarkdownSegment
}

/**
 * Splits a Markdown document into standard [MarkdownSegment.Prose] runs and
 * interactive [MarkdownSegment.DemoIsland] markers (` ```demo:<id> `), ignoring
 * regular code blocks so non-demo fences pass cleanly to the Markdown renderer.
 */
object MarkdownSegmentParser {
    private const val CODE_FENCE: String = "```"
    private const val DEMO_PREFIX: String = "demo:"

    fun parse(markdown: String): List<MarkdownSegment> {
        if (markdown.isBlank()) return emptyList()

        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split('\n')
        val segments = mutableListOf<MarkdownSegment>()
        val proseBuffer = mutableListOf<String>()

        var inRegularFence = false
        var activeDemoId: String? = null

        fun flushProse() {
            val content = proseBuffer.joinToString("\n").trim()
            if (content.isNotEmpty()) {
                segments.add(MarkdownSegment.Prose(content))
            }
            proseBuffer.clear()
        }

        // Single pass tracks code-fence nesting so `#`/`demo:` inside normal fences are untouched.
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(CODE_FENCE)) {
                val info = trimmed.removePrefix(CODE_FENCE).trim()
                if (activeDemoId != null) {
                    segments.add(MarkdownSegment.DemoIsland(activeDemoId))
                    activeDemoId = null
                    continue
                }
                if (!inRegularFence && info.startsWith(DEMO_PREFIX)) {
                    val parsedId = info.removePrefix(DEMO_PREFIX).trim()
                    if (parsedId.isNotEmpty()) {
                        flushProse()
                        activeDemoId = parsedId
                        continue
                    }
                }
                inRegularFence = !inRegularFence
                proseBuffer.add(line)
                continue
            }

            if (activeDemoId == null) {
                proseBuffer.add(line)
            }
        }

        if (activeDemoId != null) {
            segments.add(MarkdownSegment.DemoIsland(activeDemoId))
        }
        flushProse()
        return segments.toList()
    }
}
