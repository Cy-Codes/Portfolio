package codes.cy.portfolio.core.validation

private const val MAX_SLUG_LENGTH: Int = 128
private val SLUG_REGEX: Regex = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
private val SQL_IDENTIFIER_REGEX: Regex = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$")

/**
 * Pure validation ensuring URL slugs contain only lowercase alphanumeric segments separated by hyphens.
 */
fun String.isSafeSlug(): Boolean =
    length in 1..MAX_SLUG_LENGTH && SLUG_REGEX.matches(this)

/**
 * Pure allowlist pattern guarding SQL identifiers against injection.
 */
fun String.isSafeSqlIdentifier(): Boolean =
    SQL_IDENTIFIER_REGEX.matches(this)

/**
 * Pure validator resolving an optional sort field against a closed set of allowed column names.
 */
fun String?.validateAllowedSortField(allowedFields: Set<String>): String? {
    val trimmed = this?.trim()
    if (trimmed.isNullOrEmpty()) {
        return null
    }
    require(trimmed.isSafeSqlIdentifier() && trimmed in allowedFields) {
        "Unsupported sortBy field '$trimmed'. Allowed values: ${allowedFields.sorted().joinToString(", ")}."
    }
    return trimmed
}
