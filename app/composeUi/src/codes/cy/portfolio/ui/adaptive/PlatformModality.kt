package codes.cy.portfolio.ui.adaptive

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect val defaultInputModality: InputModality

@Composable
expect fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
)

@Composable
expect fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
)
