package codes.cy.portfolio.server.publicapi.domain

/**
 * Pure Markdown-to-HTML renderer for `/read/{slug}` HTML Reader View.
 * Escapes all raw HTML input and translates `demo:<id>` fenced blocks into
 * progressive-enhancement callout links into the interactive Wasm experience.
 */
object MarkdownHtmlRenderer {
    private const val CODE_FENCE_PREFIX: String = "```"
    private const val DEMO_FENCE_PREFIX: String = "demo:"
    private const val MAX_HEADING_LEVEL: Int = 6
    private val ORDERED_ITEM_REGEX: Regex = Regex("""^\d+\.\s+(.*)$""")
    private val LINK_REGEX: Regex = Regex("""\[([^]]+)]\(([^)]+)\)""")
    private val INLINE_CODE_REGEX: Regex = Regex("""`([^`]+)`""")
    private val BOLD_REGEX: Regex = Regex("""\*\*([^*]+)\*\*""")
    private val ITALIC_REGEX: Regex = Regex("""\*([^*]+)\*""")

    fun escapeHtml(raw: String): String =
        buildString(raw.length) {
            raw.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }

    fun renderToHtml(markdown: String, interactiveSlug: String): String {
        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split('\n')
        val htmlBlocks = mutableListOf<String>()
        var index = 0

        // Sequential index scan preserves fenced code/demo boundaries and contiguous list blocks without regex backtracking.
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> index += 1

                trimmed.startsWith(CODE_FENCE_PREFIX) -> {
                    val infoString = trimmed.removePrefix(CODE_FENCE_PREFIX).trim()
                    val codeLines = mutableListOf<String>()
                    index += 1
                    while (index < lines.size && !lines[index].trim().startsWith(CODE_FENCE_PREFIX)) {
                        codeLines.add(lines[index])
                        index += 1
                    }
                    if (index < lines.size) index += 1
                    htmlBlocks.add(renderCodeOrDemoBlock(infoString, codeLines, interactiveSlug))
                }

                trimmed == "---" || trimmed == "***" -> {
                    htmlBlocks.add("<hr />")
                    index += 1
                }

                isHeadingLine(trimmed) -> {
                    htmlBlocks.add(renderHeading(trimmed))
                    index += 1
                }

                trimmed.startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (index < lines.size && lines[index].trim().startsWith(">")) {
                        quoteLines.add(lines[index].trim().removePrefix(">").trim())
                        index += 1
                    }
                    htmlBlocks.add("<blockquote><p>${renderInline(quoteLines.joinToString(" "))}</p></blockquote>")
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val listItems = mutableListOf<String>()
                    while (
                        index < lines.size &&
                        (lines[index].trim().startsWith("- ") || lines[index].trim().startsWith("* "))
                    ) {
                        listItems.add("<li>${renderInline(lines[index].trim().drop(2).trim())}</li>")
                        index += 1
                    }
                    htmlBlocks.add("<ul>${listItems.joinToString("")}</ul>")
                }

                ORDERED_ITEM_REGEX.matches(trimmed) -> {
                    val listItems = mutableListOf<String>()
                    while (index < lines.size) {
                        val match = ORDERED_ITEM_REGEX.matchEntire(lines[index].trim()) ?: break
                        listItems.add("<li>${renderInline(match.groupValues[1].trim())}</li>")
                        index += 1
                    }
                    htmlBlocks.add("<ol>${listItems.joinToString("")}</ol>")
                }

                else -> {
                    val paragraphLines = mutableListOf<String>()
                    while (
                        index < lines.size &&
                        lines[index].trim().isNotEmpty() &&
                        !isBlockBoundary(lines[index].trim())
                    ) {
                        paragraphLines.add(lines[index].trim())
                        index += 1
                    }
                    htmlBlocks.add("<p>${renderInline(paragraphLines.joinToString(" "))}</p>")
                }
            }
        }

        return htmlBlocks.joinToString(separator = "\n")
    }

    private fun isHeadingLine(trimmed: String): Boolean {
        val hashes = trimmed.takeWhile { it == '#' }.length
        return hashes in 1..MAX_HEADING_LEVEL && trimmed.getOrNull(hashes) == ' '
    }

    private fun isBlockBoundary(trimmed: String): Boolean =
        trimmed.startsWith(CODE_FENCE_PREFIX) ||
            trimmed == "---" ||
            trimmed == "***" ||
            isHeadingLine(trimmed) ||
            trimmed.startsWith(">") ||
            trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") ||
            ORDERED_ITEM_REGEX.matches(trimmed)

    private fun renderHeading(trimmed: String): String {
        val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, MAX_HEADING_LEVEL)
        val text = trimmed.drop(level).trim()
        return "<h$level>${renderInline(text)}</h$level>"
    }

    private fun renderCodeOrDemoBlock(
        infoString: String,
        codeLines: List<String>,
        interactiveSlug: String,
    ): String {
        if (infoString.startsWith(DEMO_FENCE_PREFIX)) {
            val demoId = escapeHtml(infoString.removePrefix(DEMO_FENCE_PREFIX).trim())
            val safeSlug = escapeHtml(interactiveSlug)
            return """
                <aside class="demo-callout" data-demo-id="$demoId">
                    <strong>Interactive Demo: <code>$demoId</code></strong>
                    <p>This article includes an interactive Compose Multiplatform demo not rendered in static HTML Reader View.</p>
                    <p><a href="/writeups/$safeSlug">Open interactive demo view</a></p>
                </aside>
            """.trimIndent()
        }
        val languageClass = infoString.takeIf { it.isNotEmpty() }
            ?.let { " class=\"language-${escapeHtml(it)}\"" }
            .orEmpty()
        val escapedCode = escapeHtml(codeLines.joinToString("\n"))
        return "<pre><code$languageClass>$escapedCode</code></pre>"
    }

    private fun renderInline(raw: String): String {
        val placeholders = mutableListOf<String>()
        val withCodeTokens = INLINE_CODE_REGEX.replace(raw) { match ->
            val tokenIndex = placeholders.size
            placeholders.add("<code>${escapeHtml(match.groupValues[1])}</code>")
            "\u0000CODE$tokenIndex\u0000"
        }
        val withLinks = LINK_REGEX.replace(withCodeTokens) { match ->
            val label = escapeHtml(match.groupValues[1])
            val href = sanitizeHref(match.groupValues[2])
            if (href == null) {
                label
            } else {
                val tokenIndex = placeholders.size
                placeholders.add("<a href=\"${escapeHtml(href)}\">$label</a>")
                "\u0000CODE$tokenIndex\u0000"
            }
        }
        val escaped = escapeHtml(withLinks)
        val formatted = escaped
            .let { text -> BOLD_REGEX.replace(text) { "<strong>${it.groupValues[1]}</strong>" } }
            .let { text -> ITALIC_REGEX.replace(text) { "<em>${it.groupValues[1]}</em>" } }

        return placeholders.foldIndexed(formatted) { idx, acc, htmlFragment ->
            acc.replace("\u0000CODE$idx\u0000", htmlFragment)
        }
    }

    private fun sanitizeHref(rawHref: String): String? {
        val trimmed = rawHref.trim()
        return if (
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("/")
        ) {
            trimmed
        } else {
            null
        }
    }
}
