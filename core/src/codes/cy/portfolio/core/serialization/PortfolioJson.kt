package codes.cy.portfolio.core.serialization

import kotlinx.serialization.json.Json

/**
 * Frozen JSON serialization contract shared across all KMP clients and Ktor JVM servers.
 * Preserves default-valued properties and `is*` boolean field names identically.
 */
val PortfolioJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = true
}
