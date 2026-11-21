package codes.cy.portfolio.presentation.navigation

import codes.cy.portfolio.core.validation.isSafeSlug
import kotlinx.serialization.Serializable

@Serializable
enum class TopLevelDestination(val urlPath: String) {
    PROFILE("/"),
    PROJECTS("/projects"),
    WRITEUPS("/writeups"),
    RESUME_DOCS("/resume"),
}

@Serializable
sealed interface RootConfig {
    @Serializable
    data object Profile : RootConfig

    @Serializable
    data class Projects(val initialSlug: String? = null) : RootConfig

    @Serializable
    data class Writeups(val initialSlug: String? = null) : RootConfig

    @Serializable
    data object ResumeDocs : RootConfig
}

/**
 * Pure bidirectional URL path <-> [RootConfig] mapper shared by Decompose `WebNavigation`
 * and client deep-link entry points.
 */
object PortfolioDeepLink {
    fun toDestination(config: RootConfig): TopLevelDestination =
        when (config) {
            is RootConfig.Profile -> TopLevelDestination.PROFILE
            is RootConfig.Projects -> TopLevelDestination.PROJECTS
            is RootConfig.Writeups -> TopLevelDestination.WRITEUPS
            is RootConfig.ResumeDocs -> TopLevelDestination.RESUME_DOCS
        }

    fun toConfig(destination: TopLevelDestination): RootConfig =
        when (destination) {
            TopLevelDestination.PROFILE -> RootConfig.Profile
            TopLevelDestination.PROJECTS -> RootConfig.Projects()
            TopLevelDestination.WRITEUPS -> RootConfig.Writeups()
            TopLevelDestination.RESUME_DOCS -> RootConfig.ResumeDocs
        }

    fun toPath(config: RootConfig): String =
        when (config) {
            is RootConfig.Profile -> "/"
            is RootConfig.Projects ->
                config.initialSlug?.takeIf { it.isSafeSlug() }?.let { "/projects/$it" } ?: "/projects"
            is RootConfig.Writeups ->
                config.initialSlug?.takeIf { it.isSafeSlug() }?.let { "/writeups/$it" } ?: "/writeups"
            is RootConfig.ResumeDocs -> "/resume"
        }

    fun fromPath(rawPath: String?): RootConfig {
        val normalized = rawPath
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.trim()
            ?.trimEnd('/')
            ?.ifEmpty { "/" }
            ?: "/"

        if (normalized == "/" || normalized == "/about") {
            return RootConfig.Profile
        }
        if (normalized == "/resume") {
            return RootConfig.ResumeDocs
        }
        if (normalized == "/projects") {
            return RootConfig.Projects()
        }
        if (normalized.startsWith("/projects/")) {
            val slug = normalized.removePrefix("/projects/").substringBefore('/')
            return RootConfig.Projects(initialSlug = slug.takeIf { it.isSafeSlug() })
        }
        if (normalized == "/writeups") {
            return RootConfig.Writeups()
        }
        if (normalized.startsWith("/writeups/")) {
            val slug = normalized.removePrefix("/writeups/").substringBefore('/')
            return RootConfig.Writeups(initialSlug = slug.takeIf { it.isSafeSlug() })
        }
        return RootConfig.Profile
    }
}
