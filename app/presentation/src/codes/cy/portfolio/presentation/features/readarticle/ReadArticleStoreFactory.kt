package codes.cy.portfolio.presentation.features.readarticle

import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.ClientPlatform
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.ViewportTier
import codes.cy.portfolio.presentation.data.AnalyticsEventSender
import codes.cy.portfolio.presentation.data.PublicPortfolioApi
import codes.cy.portfolio.presentation.data.fold
import codes.cy.portfolio.presentation.plugins.installAnalyticsPlugin
import codes.cy.portfolio.presentation.plugins.installPortfolioErrorRecovery
import codes.cy.portfolio.presentation.plugins.installPortfolioLogging
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.StoreBuilder
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

object ReadArticleStoreFactory {
    fun create(
        slug: String,
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        debuggable: Boolean = false,
    ): Store<ReadArticleState, ReadArticleIntent, ReadArticleAction> =
        store<ReadArticleState, ReadArticleIntent, ReadArticleAction>(
            initial = ReadArticleState.Loading(slug = slug),
        ) {
            configureStore(
                slug = slug,
                api = api,
                analyticsSender = analyticsSender,
                platform = platform,
                viewportTierProvider = viewportTierProvider,
                debuggable = debuggable,
            )
        }

    fun StoreBuilder<ReadArticleState, ReadArticleIntent, ReadArticleAction>.configureStore(
        slug: String,
        api: PublicPortfolioApi,
        analyticsSender: AnalyticsEventSender,
        platform: ClientPlatform,
        viewportTierProvider: () -> ViewportTier,
        debuggable: Boolean = false,
    ) {
        configure {
            name = "ReadArticleStore-$slug"
            this.debuggable = debuggable
            allowTransientSubscriptions = true
        }
        installPortfolioLogging(debuggable = debuggable, tag = "ReadArticleStore")
        installPortfolioErrorRecovery { apiError ->
            updateState { ReadArticleState.Error(slug = slug, error = apiError) }
        }
        installAnalyticsPlugin(
            analyticsSender = analyticsSender,
            platform = platform,
            viewportTierProvider = viewportTierProvider,
        ) { intent, clientPlatform, tier ->
            if (intent is ReadArticleIntent.Load || intent is ReadArticleIntent.Retry) {
                TrackEventRequest(
                    eventType = AnalyticsEventType.WRITEUP_READ,
                    path = "/writeups/$slug",
                    platform = clientPlatform,
                    viewportTier = tier,
                )
            } else {
                null
            }
        }
        init {
            loadArticle(slug = slug, api = api)
        }
        reduce { intent ->
            when (intent) {
                is ReadArticleIntent.Load,
                is ReadArticleIntent.Retry -> loadArticle(slug = slug, api = api)
                is ReadArticleIntent.NavigateBack -> action(ReadArticleAction.NavigateBack)
                is ReadArticleIntent.OpenReaderHtmlVersion ->
                    action(ReadArticleAction.OpenExternalUrl(api.buildReaderHtmlUrl(slug)))
            }
        }
    }

    private suspend fun PipelineContext<ReadArticleState, ReadArticleIntent, ReadArticleAction>.loadArticle(
        slug: String,
        api: PublicPortfolioApi,
    ) {
        updateState { ReadArticleState.Loading(slug = slug) }
        api.getWriteupBySlug(slug).fold(
            onSuccess = { writeup ->
                val outline = MarkdownOutlineExtractor.extract(writeup.bodyMarkdown)
                updateState {
                    ReadArticleState.Content(
                        slug = writeup.slug,
                        writeup = writeup,
                        tableOfContents = outline.headings,
                        embeddedDemoIds = outline.embeddedDemoIds,
                    )
                }
            },
            onFailure = { apiError ->
                updateState { ReadArticleState.Error(slug = slug, error = apiError) }
            },
        )
    }
}
