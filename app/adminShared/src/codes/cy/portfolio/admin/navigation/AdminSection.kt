package codes.cy.portfolio.admin.navigation

import kotlinx.serialization.Serializable

enum class AdminSection(
    val shortLabel: String,
    val drawerLabel: String,
    val webPath: String,
) {
    STATS("Stats", "Analytics Overview", "/admin/stats"),
    CAREER("Career", "Profile & Career", "/admin/career"),
    PROJECTS("Projects", "Projects CMS", "/admin/projects"),
    WRITEUPS("Writeups", "Writeups Editor", "/admin/writeups"),
    DOCUMENTS("Docs", "Documents & R2", "/admin/documents"),
}

@Serializable
sealed interface AdminConfig {
    @Serializable
    data object SignIn : AdminConfig

    @Serializable
    data object Stats : AdminConfig

    @Serializable
    data object Career : AdminConfig

    @Serializable
    data object Projects : AdminConfig

    @Serializable
    data object Writeups : AdminConfig

    @Serializable
    data object Documents : AdminConfig
}

fun AdminSection.toConfig(): AdminConfig =
    when (this) {
        AdminSection.STATS -> AdminConfig.Stats
        AdminSection.CAREER -> AdminConfig.Career
        AdminSection.PROJECTS -> AdminConfig.Projects
        AdminSection.WRITEUPS -> AdminConfig.Writeups
        AdminSection.DOCUMENTS -> AdminConfig.Documents
    }
