package codes.cy.portfolio.admin.features

private const val WORDS_PER_MINUTE: Int = 200
private const val MIN_READING_MINUTES: Int = 1
private val WHITESPACE_SPLIT_REGEX: Regex = Regex("\\s+")
private val NON_SLUG_CHARS_REGEX: Regex = Regex("[^a-z0-9\\s-]")
private val MULTI_HYPHEN_REGEX: Regex = Regex("-+")
private const val MAX_SLUG_LENGTH: Int = 128

fun slugifyTitle(rawTitle: String): String =
    rawTitle.trim()
        .lowercase()
        .replace(NON_SLUG_CHARS_REGEX, "")
        .replace(WHITESPACE_SPLIT_REGEX, "-")
        .replace(MULTI_HYPHEN_REGEX, "-")
        .trim('-')
        .take(MAX_SLUG_LENGTH)
        .trimEnd('-')

fun estimateReadingTimeMinutes(markdown: String): Int {
    val wordCount = markdown.trim()
        .takeIf { it.isNotEmpty() }
        ?.split(WHITESPACE_SPLIT_REGEX)
        ?.count { it.isNotEmpty() }
        ?: 0
    if (wordCount == 0) return MIN_READING_MINUTES
    val rounded = (wordCount + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE
    return rounded.coerceAtLeast(MIN_READING_MINUTES)
}

fun buildDemoFenceSnippet(demoId: String): String =
    "```demo:${demoId.trim()}\n```"
