package codes.cy.portfolio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.SocialLinkDto
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.presentation.data.AnalyticsEventSender
import codes.cy.portfolio.presentation.data.ApiResult
import codes.cy.portfolio.presentation.data.PublicPortfolioApi
import codes.cy.portfolio.presentation.navigation.DefaultRootComponent
import codes.cy.portfolio.presentation.navigation.PresentationDependencies
import codes.cy.portfolio.ui.PortfolioApp
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.reload.DevelopmentEntryPoint

/**
 * Self-contained Compose preview & temporary iOS fallback entry point prior to Phase 5 SwiftUI integration.
 */
@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    val rootComponent = rememberPreviewRootComponent()
    PortfolioApp(rootComponent = rootComponent)
}

@Composable
private fun rememberPreviewRootComponent(): DefaultRootComponent {
    val lifecycle = remember { LifecycleRegistry() }
    val root = remember(lifecycle) {
        val showcaseApi = ShowcasePreviewPortfolioApi()
        val dependencies = PresentationDependencies(
            api = showcaseApi,
            analyticsSender = showcaseApi,
            platform = ClientPlatform.IOS,
            viewportTierProvider = { ViewportTier.COMPACT },
        )
        DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            baseDependencies = dependencies,
        )
    }

    DisposableEffect(lifecycle) {
        lifecycle.resume()
        onDispose { lifecycle.destroy() }
    }

    return root
}

private class ShowcasePreviewPortfolioApi : PublicPortfolioApi, AnalyticsEventSender {
    private val sampleInstant: Instant = Instant.parse("2026-11-20T12:00:00Z")

    private val skillKmp = SkillDto("s1", "Kotlin Multiplatform", "Mobile & KMP", isFeatured = true, sortOrder = 1)
    private val skillKtor = SkillDto("s2", "Ktor", "Backend", isFeatured = true, sortOrder = 2)
    private val skillPg = SkillDto("s3", "PostgreSQL", "Databases", isFeatured = true, sortOrder = 3)

    private val sampleProject = ProjectDetailDto(
        id = "p1",
        slug = "adaptive-kmp-portfolio",
        title = "Adaptive KMP Portfolio Platform",
        summary = "Multiplatform portfolio across Android, Desktop, Wasm Web, and native SwiftUI iOS.",
        caseStudyMarkdown = "## Architecture\nShared Decompose + FlowMVI presentation layer with split Ktor JVM backends.\n\n```kotlin\nval options = QueryOptions(page = 1, pageSize = 20)\n```",
        repoUrl = "https://github.com/cy/Portfolio",
        liveUrl = "https://cy.codes",
        status = ProjectStatus.ACTIVE,
        isFeatured = true,
        skills = listOf(skillKmp, skillKtor, skillPg),
        sortOrder = 1,
        createdAt = sampleInstant,
        updatedAt = sampleInstant,
    )

    private val sampleWriteup = WriteupDetailDto(
        id = "w1",
        slug = "decompose-flowmvi-with-swiftui-and-wasm",
        title = "Sharing Decompose + FlowMVI Across SwiftUI and Kotlin/Wasm",
        subtitle = "Keeping presentation Compose-free with interactive demo islands",
        summary = "A practical blueprint for sharing MVI state and browser-synced routing across native SwiftUI and Compose.",
        bodyMarkdown = "# Why Compose-Free Presentation Matters\n\nSeparating `app/presentation` from `app/composeUi` keeps business logic shared.\n\n```demo:adaptive-breakpoints\n```",
        tags = listOf("kotlin-multiplatform", "decompose", "flowmvi"),
        status = WriteupStatus.PUBLISHED,
        readingTimeMin = 6,
        publishedAt = sampleInstant,
        createdAt = sampleInstant,
        updatedAt = sampleInstant,
    )

    override suspend fun getPortfolioOverview(): ApiResult<PortfolioOverviewDto> =
        ApiResult.Success(
            PortfolioOverviewDto(
                profile = ProfileDto(
                    fullName = "CY",
                    headline = "Staff Kotlin Multiplatform & Backend Systems Engineer",
                    bioMarkdown = "Building cross-platform systems with **Kotlin Multiplatform**, **Compose**, **Ktor**, and **PostgreSQL**.",
                    location = "United States",
                    availability = "Open to selective advisory engagements",
                    socialLinks = listOf(SocialLinkDto("GitHub", "https://github.com/cy")),
                    updatedAt = sampleInstant,
                ),
                experiences = listOf(
                    ExperienceDto(
                        id = "e1",
                        company = "Cloud & Mobile Systems Lab",
                        title = "Principal Platform Engineer",
                        location = "Remote",
                        startDate = LocalDate.parse("2024-03-01"),
                        endDate = null,
                        isCurrent = true,
                        highlights = listOf("Architected Compose-free Decompose + FlowMVI layer across 4 client platforms"),
                        skills = listOf(skillKmp, skillKtor),
                        sortOrder = 1,
                        createdAt = sampleInstant,
                        updatedAt = sampleInstant,
                    ),
                ),
                featuredSkills = listOf(skillKmp, skillKtor, skillPg),
            ),
        )

    override suspend fun getProjects(
        options: QueryOptions,
        featuredOnly: Boolean?,
        skillName: String?,
    ): ApiResult<PagedResult<ProjectSummaryDto>> =
        ApiResult.Success(
            PagedResult.of(
                items = listOf(
                    ProjectSummaryDto(
                        id = sampleProject.id,
                        slug = sampleProject.slug,
                        title = sampleProject.title,
                        summary = sampleProject.summary,
                        repoUrl = sampleProject.repoUrl,
                        liveUrl = sampleProject.liveUrl,
                        status = sampleProject.status,
                        isFeatured = sampleProject.isFeatured,
                        skills = sampleProject.skills,
                        sortOrder = sampleProject.sortOrder,
                        updatedAt = sampleProject.updatedAt,
                    ),
                ),
                totalCount = 1L,
                options = options,
            ),
        )

    override suspend fun getProjectBySlug(slug: String): ApiResult<ProjectDetailDto> =
        if (slug == sampleProject.slug) ApiResult.Success(sampleProject)
        else ApiResult.Failure(ApiError.NotFound("Not found", "project", slug))

    override suspend fun getWriteups(
        options: QueryOptions,
        tag: String?,
        searchQuery: String?,
    ): ApiResult<PagedResult<WriteupSummaryDto>> =
        ApiResult.Success(
            PagedResult.of(
                items = listOf(
                    WriteupSummaryDto(
                        id = sampleWriteup.id,
                        slug = sampleWriteup.slug,
                        title = sampleWriteup.title,
                        subtitle = sampleWriteup.subtitle,
                        summary = sampleWriteup.summary,
                        tags = sampleWriteup.tags,
                        status = sampleWriteup.status,
                        readingTimeMin = sampleWriteup.readingTimeMin,
                        publishedAt = sampleWriteup.publishedAt,
                        updatedAt = sampleWriteup.updatedAt,
                    ),
                ),
                totalCount = 1L,
                options = options,
            ),
        )

    override suspend fun getWriteupBySlug(slug: String): ApiResult<WriteupDetailDto> =
        if (slug == sampleWriteup.slug) ApiResult.Success(sampleWriteup)
        else ApiResult.Failure(ApiError.NotFound("Not found", "writeup", slug))

    override suspend fun getDocuments(options: QueryOptions): ApiResult<PagedResult<DocumentDto>> =
        ApiResult.Success(
            PagedResult.of(
                items = listOf(
                    DocumentDto(
                        id = "10000000-0000-0000-0000-000000000001",
                        title = "Engineering Resume (2026)",
                        category = DocumentCategory.RESUME,
                        originalFilename = "resume-2026.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 184_320L,
                        sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        storageKey = "seed/resume-2026.pdf",
                        isPublic = true,
                        createdAt = sampleInstant,
                    ),
                ),
                totalCount = 1L,
                options = options,
            ),
        )

    override fun buildDocumentDownloadUrl(documentId: String): String = "/api/v1/public/documents/$documentId/download"
    override fun buildReaderHtmlUrl(slug: String): String = "/read/$slug"
    override suspend fun recordEvent(request: TrackEventRequest): ApiResult<Unit> = ApiResult.Success(Unit)
}
