package codes.cy.portfolio.server.publicapi.api

import codes.cy.portfolio.core.routes.ReadWriteupPageRoute
import codes.cy.portfolio.server.publicapi.application.PublicPortfolioQueryService
import codes.cy.portfolio.server.publicapi.domain.MarkdownHtmlRenderer
import codes.cy.portfolio.server.publicapi.domain.SeoMetadataBuilder
import codes.cy.portfolio.server.publicapi.domain.SeoPageMetadata
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.path
import io.ktor.server.resources.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File
import kotlinx.html.HEAD
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.noScript
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

private val WASM_SHELL_STYLESHEET: String = """
    html, body, #portfolio-root {
        width: 100%;
        height: 100%;
        margin: 0;
        padding: 0;
        overflow: hidden;
    }
""".trimIndent()

private data class WasmShellAssets(
    val importMapLoaderSrc: String? = null,
    val scriptSrc: String? = null,
    val isEsModule: Boolean = false,
    val stylesheetHref: String? = null,
)

private val READER_VIEW_STYLESHEET: String = """
    :root { color-scheme: light dark; }
    body {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        line-height: 1.65;
        max-width: 74ch;
        margin: 0 auto;
        padding: 2rem 1.25rem 4rem;
    }
    header.site-nav {
        display: flex;
        justify-content: space-between;
        align-items: center;
        border-bottom: 1px solid rgba(127, 127, 127, 0.3);
        padding-bottom: 0.75rem;
        margin-bottom: 2rem;
    }
    header.site-nav a { margin-right: 1rem; text-decoration: none; font-weight: 600; }
    .meta-row { opacity: 0.8; font-size: 0.92rem; margin-bottom: 1.5rem; }
    .tag-pill {
        display: inline-block;
        padding: 0.15rem 0.55rem;
        margin-right: 0.4rem;
        border: 1px solid rgba(127, 127, 127, 0.35);
        border-radius: 999px;
        font-size: 0.82rem;
    }
    pre {
        overflow-x: auto;
        padding: 1rem;
        border-radius: 8px;
        border: 1px solid rgba(127, 127, 127, 0.25);
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.9rem;
    }
    code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.92em; }
    blockquote { border-left: 3px solid currentColor; padding-left: 1rem; margin-left: 0; opacity: 0.9; }
    .demo-callout {
        border: 1px solid rgba(127, 127, 127, 0.45);
        border-radius: 8px;
        padding: 1rem 1.25rem;
        margin: 1.5rem 0;
    }
    footer.reader-footer {
        margin-top: 3rem;
        padding-top: 1rem;
        border-top: 1px solid rgba(127, 127, 127, 0.3);
        font-size: 0.92rem;
    }
""".trimIndent()

fun Route.registerPublicSeoRoutes(
    queryService: PublicPortfolioQueryService,
    siteBaseUrl: String,
    webStaticDir: String?,
) {
    get<ReadWriteupPageRoute> { route ->
        val writeup = queryService.getWriteupBySlug(route.slug)
        val seoMeta = SeoMetadataBuilder.forWriteup(
            baseUrl = siteBaseUrl,
            canonicalPath = "/read/${writeup.slug}",
            writeup = writeup,
        )
        val renderedBodyHtml = MarkdownHtmlRenderer.renderToHtml(
            markdown = writeup.bodyMarkdown,
            interactiveSlug = writeup.slug,
        )
        call.respondHtml(HttpStatusCode.OK) {
            lang = "en"
            head {
                applySeoHead(seoMeta)
                style { unsafe { +READER_VIEW_STYLESHEET } }
            }
            body {
                header(classes = "site-nav") {
                    nav {
                        a(href = "/") { +"Portfolio" }
                        a(href = "/writeups") { +"Writeups" }
                        a(href = "/projects") { +"Projects" }
                    }
                    a(href = "/writeups/${writeup.slug}") { +"Interactive App View" }
                }
                main {
                    article {
                        h1 { +writeup.title }
                        writeup.subtitle?.let { sub ->
                            p { +sub }
                        }
                        div(classes = "meta-row") {
                            span { +"${writeup.readingTimeMin} min read" }
                            writeup.publishedAt?.let { published ->
                                span { +" · Published $published" }
                            }
                        }
                        if (writeup.tags.isNotEmpty()) {
                            div {
                                writeup.tags.forEach { tag ->
                                    span(classes = "tag-pill") { +tag }
                                }
                            }
                        }
                        div {
                            unsafe { +renderedBodyHtml }
                        }
                    }
                }
                footer(classes = "reader-footer") {
                    p {
                        +"Prefer live interactive Compose Multiplatform diagrams? "
                        a(href = "/writeups/${writeup.slug}") { +"Open interactive writeup" }
                    }
                }
            }
        }
    }

    get("/sitemap.xml") {
        call.respondText(
            text = queryService.loadSitemapXml(),
            contentType = ContentType.Application.Xml,
            status = HttpStatusCode.OK,
        )
    }

    get("/robots.txt") {
        call.respondText(
            text = queryService.getRobotsTxt(),
            contentType = ContentType.Text.Plain,
            status = HttpStatusCode.OK,
        )
    }

    val staticRoot = webStaticDir?.let(::File)?.takeIf { it.isDirectory }
    val shellAssets = resolveWasmShellAssets(staticRoot)

    if (staticRoot != null) {
        staticFiles("/", staticRoot) {
            exclude { file -> file.name == "index.html" }
        }
    }

    listOf(
        "/",
        "/about",
        "/resume",
        "/projects",
        "/projects/{slug}",
        "/writeups",
        "/writeups/{slug}",
    ).forEach { spaRoute ->
        get(spaRoute) {
            call.respondWasmShellHtml(
                queryService = queryService,
                shellAssets = shellAssets,
            )
        }
    }
}

private fun resolveWasmShellAssets(staticRoot: File?): WasmShellAssets {
    if (staticRoot == null) return WasmShellAssets()
    val importMapLoader = if (staticRoot.resolve("import-map-loader.js").isFile) "/import-map-loader.js" else null
    val stylesheet = if (staticRoot.resolve("styles.css").isFile) "/styles.css" else null
    return when {
        staticRoot.resolve("webApp.mjs").isFile -> WasmShellAssets(
            importMapLoaderSrc = importMapLoader,
            scriptSrc = "/webApp.mjs",
            isEsModule = true,
            stylesheetHref = stylesheet,
        )
        staticRoot.resolve("webApp.js").isFile -> WasmShellAssets(
            scriptSrc = "/webApp.js",
            isEsModule = false,
            stylesheetHref = stylesheet,
        )
        staticRoot.resolve("composeApp.js").isFile -> WasmShellAssets(
            scriptSrc = "/composeApp.js",
            isEsModule = false,
            stylesheetHref = stylesheet,
        )
        else -> WasmShellAssets(stylesheetHref = stylesheet)
    }
}

private suspend fun ApplicationCall.respondWasmShellHtml(
    queryService: PublicPortfolioQueryService,
    shellAssets: WasmShellAssets,
) {
    val seoMeta = queryService.resolveSeoMetadata(request.path())
    respondHtml(HttpStatusCode.OK) {
        lang = "en"
        head {
            applySeoHead(seoMeta)
            style { unsafe { +WASM_SHELL_STYLESHEET } }
            shellAssets.stylesheetHref?.let { href ->
                link(rel = "stylesheet", href = href)
            }
        }
        body {
            div { id = "portfolio-root" }
            shellAssets.importMapLoaderSrc?.let { loaderUrl ->
                script(src = loaderUrl) {}
            }
            shellAssets.scriptSrc?.let { scriptUrl ->
                script(src = scriptUrl) {
                    if (shellAssets.isEsModule) {
                        attributes["type"] = "module"
                    }
                }
            }
            noScript {
                main {
                    h1 { +seoMeta.noscriptHeading }
                    p { +seoMeta.noscriptSummary }
                    p {
                        a(href = "/sitemap.xml") { +"Browse XML Sitemap" }
                    }
                }
            }
        }
    }
}

private fun HEAD.applySeoHead(seoMeta: SeoPageMetadata) {
    meta(charset = "UTF-8")
    meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
    title { +seoMeta.title }
    meta(name = "description", content = seoMeta.description)
    link(rel = "canonical", href = seoMeta.canonicalUrl)
    meta {
        attributes["property"] = "og:title"
        content = seoMeta.title
    }
    meta {
        attributes["property"] = "og:description"
        content = seoMeta.description
    }
    meta {
        attributes["property"] = "og:type"
        content = seoMeta.ogType
    }
    meta {
        attributes["property"] = "og:url"
        content = seoMeta.canonicalUrl
    }
    script(type = "application/ld+json") {
        unsafe { +seoMeta.jsonLdJson }
    }
}
