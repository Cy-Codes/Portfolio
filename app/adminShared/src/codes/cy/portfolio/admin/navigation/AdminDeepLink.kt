package codes.cy.portfolio.admin.navigation

object AdminDeepLink {
    fun sectionFromPath(rawPath: String?): AdminSection {
        val normalizedPath = normalizePath(rawPath)
        val segments = normalizedPath.trim('/').split('/').filter { it.isNotEmpty() }
        val routeSegments = if (segments.firstOrNull() == "admin") segments.drop(1) else segments
        return when (routeSegments.firstOrNull()?.lowercase()) {
            "career", "profile" -> AdminSection.CAREER
            "projects" -> AdminSection.PROJECTS
            "writeups", "articles" -> AdminSection.WRITEUPS
            "documents", "docs" -> AdminSection.DOCUMENTS
            else -> AdminSection.STATS
        }
    }

    fun toWebPath(config: AdminConfig): String =
        when (config) {
            is AdminConfig.SignIn -> "/admin/signin"
            is AdminConfig.Stats -> AdminSection.STATS.webPath
            is AdminConfig.Career -> AdminSection.CAREER.webPath
            is AdminConfig.Projects -> AdminSection.PROJECTS.webPath
            is AdminConfig.Writeups -> AdminSection.WRITEUPS.webPath
            is AdminConfig.Documents -> AdminSection.DOCUMENTS.webPath
        }

    fun toSectionOrNull(config: AdminConfig): AdminSection? =
        when (config) {
            is AdminConfig.SignIn -> null
            is AdminConfig.Stats -> AdminSection.STATS
            is AdminConfig.Career -> AdminSection.CAREER
            is AdminConfig.Projects -> AdminSection.PROJECTS
            is AdminConfig.Writeups -> AdminSection.WRITEUPS
            is AdminConfig.Documents -> AdminSection.DOCUMENTS
        }

    private fun normalizePath(rawInput: String?): String {
        val raw = rawInput?.trim().orEmpty()
        if (raw.isEmpty()) return "/"
        val withoutSchemeAndHost = if (raw.contains("://")) {
            val afterScheme = raw.substringAfter("://")
            val slashIdx = afterScheme.indexOf('/')
            if (slashIdx >= 0) afterScheme.substring(slashIdx) else "/"
        } else {
            raw
        }
        val cleanPath = withoutSchemeAndHost.substringBefore('?').substringBefore('#')
        return if (cleanPath.startsWith('/')) cleanPath else "/$cleanPath"
    }
}
