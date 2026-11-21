package codes.cy.portfolio.presentation.features.readarticle

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.presentation.data.toDisplayMessage
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface ReadArticleState : MVIState {
    val slug: String

    data class Loading(
        override val slug: String,
    ) : ReadArticleState

    data class Content(
        override val slug: String,
        val writeup: WriteupDetailDto,
        val tableOfContents: List<TocHeading>,
        val embeddedDemoIds: List<String>,
    ) : ReadArticleState

    data class Error(
        override val slug: String,
        val error: ApiError,
    ) : ReadArticleState {
        val displayMessage: String
            get() = error.toDisplayMessage()
    }
}

sealed interface ReadArticleIntent : MVIIntent {
    data object Load : ReadArticleIntent
    data object Retry : ReadArticleIntent
    data object NavigateBack : ReadArticleIntent
    data object OpenReaderHtmlVersion : ReadArticleIntent
}

sealed interface ReadArticleAction : MVIAction {
    data object NavigateBack : ReadArticleAction
    data class OpenExternalUrl(val url: String) : ReadArticleAction
}
