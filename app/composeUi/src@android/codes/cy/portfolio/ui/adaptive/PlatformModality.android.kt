package codes.cy.portfolio.ui.adaptive

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val defaultInputModality: InputModality = InputModality.TOUCH

@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
) = Unit

@Composable
actual fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier,
) = Unit
