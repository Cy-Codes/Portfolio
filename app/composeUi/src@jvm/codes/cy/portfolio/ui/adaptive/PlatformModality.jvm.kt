package codes.cy.portfolio.ui.adaptive

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val defaultInputModality: InputModality = InputModality.POINTER

@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
    )
}

@Composable
actual fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier,
    )
}
