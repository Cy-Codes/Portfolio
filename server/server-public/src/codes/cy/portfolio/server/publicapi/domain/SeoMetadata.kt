package codes.cy.portfolio.server.publicapi.domain

import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.serialization.PortfolioJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SeoPageMetadata(
    val title: String,
    val description: String,
    val canonicalUrl: String,
    val ogType: String,
    val jsonLdJson: String,
    val noscriptHeading: String,
    val noscriptSummary: String,
)

@Serializable
private data class JsonLdPerson(
    @SerialName("@type")
    val type: String = "Person",
    val name: String,
    val jobTitle: String,
    val url: String,
    val sameAs: List<String> = emptyList(),
)

@Serializable
private data class JsonLdProfilePage(
    @SerialName("@context")
    val context: String = SCHEMA_ORG_CONTEXT,
    @SerialName("@type")
    val type: String = "ProfilePage",
    val url: String,
    val name: String,
    val description: String,
    val mainEntity: JsonLdPerson,
)

@Serializable
private data class JsonLdArticle(
    @SerialName("@context")
    val context: String = SCHEMA_ORG_CONTEXT,
    @SerialName("@type")
    val type: String = "TechArticle",
    val headline: String,
    val description: String,
    val url: String,
    val datePublished: String? = null,
    val dateModified: String,
    val keywords: List<String> = emptyList(),
)

private const val SCHEMA_ORG_CONTEXT: String = "https://schema.org"

/**
 * Pure SEO metadata, JSON-LD (`ProfilePage` / `TechArticle`), `sitemap.xml`, and `robots.txt` builders.
 */
object SeoMetadataBuilder {
    private const val DEFAULT_SITE_NAME: String = "Portfolio"
    private const val DEFAULT_DESCRIPTION: String =
        "Engineering portfolio, architecture case studies, and interactive technical writeups."

    fun forHome(
        baseUrl: String,
        canonicalPath: String,
        overview: PortfolioOverviewDto,
    ): SeoPageMetadata {
        val profile = overview.profile
        val personName = profile?.fullName ?: DEFAULT_SITE_NAME
        val headline = profile?.headline ?: DEFAULT_DESCRIPTION
        val canonicalUrl = buildCanonicalUrl(baseUrl, canonicalPath)
        val sameAs = profile?.socialLinks.orEmpty().map { it.url }
        val jsonLd = encodeJsonLd(
            JsonLdProfilePage.serializer(),
            JsonLdProfilePage(
                url = canonicalUrl,
                name = "$personName — Engineering Portfolio",
                description = headline,
                mainEntity = JsonLdPerson(
                    name = personName,
                    jobTitle = headline,
                    url = baseUrl,
                    sameAs = sameAs,
                ),
            ),
        )
        return SeoPageMetadata(
            title = "$personName — $headline",
            description = headline,
            canonicalUrl = canonicalUrl,
            ogType = "profile",
            jsonLdJson = jsonLd,
            noscriptHeading = personName,
            noscriptSummary = headline,
        )
    }

    fun forProject(
        baseUrl: String,
        project: ProjectDetailDto,
    ): SeoPageMetadata {
        val canonicalUrl = buildCanonicalUrl(baseUrl, "/projects/${project.slug}")
        val jsonLd = encodeJsonLd(
            JsonLdArticle.serializer(),
            JsonLdArticle(
                headline = project.title,
                description = project.summary,
                url = canonicalUrl,
                dateModified = project.updatedAt.toString(),
                keywords = project.skills.map { it.name },
            ),
        )
        return SeoPageMetadata(
            title = "${project.title} — Case Study",
            description = project.summary,
            canonicalUrl = canonicalUrl,
            ogType = "article",
            jsonLdJson = jsonLd,
            noscriptHeading = project.title,
            noscriptSummary = project.summary,
        )
    }

    fun forWriteup(
        baseUrl: String,
        canonicalPath: String,
        writeup: WriteupDetailDto,
    ): SeoPageMetadata {
        val canonicalUrl = buildCanonicalUrl(baseUrl, canonicalPath)
        val jsonLd = encodeJsonLd(
            JsonLdArticle.serializer(),
            JsonLdArticle(
                headline = writeup.title,
                description = writeup.summary,
                url = canonicalUrl,
                datePublished = writeup.publishedAt?.toString(),
                dateModified = writeup.updatedAt.toString(),
                keywords = writeup.tags,
            ),
        )
        return SeoPageMetadata(
            title = "${writeup.title} — Technical Writeup",
            description = writeup.summary,
            canonicalUrl = canonicalUrl,
            ogType = "article",
            jsonLdJson = jsonLd,
            noscriptHeading = writeup.title,
            noscriptSummary = writeup.summary,
        )
    }

    fun renderSitemapXml(
        baseUrl: String,
        projectSlugs: List<String>,
        writeupSlugs: List<String>,
    ): String {
        val staticUrls = listOf("/", "/projects", "/writeups")
        val projectUrls = projectSlugs.map { "/projects/$it" }
        val writeupUrls = writeupSlugs.flatMap { listOf("/writeups/$it", "/read/$it") }
        val allUrls = (staticUrls + projectUrls + writeupUrls).distinct()

        val urlElements = allUrls.joinToString(separator = "\n") { path ->
            val loc = MarkdownHtmlRenderer.escapeHtml(buildCanonicalUrl(baseUrl, path))
            "  <url><loc>$loc</loc></url>"
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
            $urlElements
            </urlset>
        """.trimIndent() + "\n"
    }

    fun renderRobotsTxt(baseUrl: String): String =
        """
        User-agent: *
        Allow: /
        Disallow: /api/v1/admin/

        Sitemap: ${buildCanonicalUrl(baseUrl, "/sitemap.xml")}
        """.trimIndent() + "\n"

    private fun buildCanonicalUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (path.startsWith('/')) path else "/$path"
        return if (cleanPath == "/") "$cleanBase/" else "$cleanBase$cleanPath"
    }

    private fun <T> encodeJsonLd(
        serializer: kotlinx.serialization.SerializationStrategy<T>,
        value: T,
    ): String = PortfolioJson.encodeToString(serializer, value).replace("</", "<\\/")
}
