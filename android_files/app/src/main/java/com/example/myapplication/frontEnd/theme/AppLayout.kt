package com.example.myapplication.frontEnd.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object AppLayout {
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val FormWidth = 560.dp
    val TwoColumnWidth = 600.dp
    val ContentWidth = 720.dp
}

/** Gives screens a shared reading width while leaving each screen in charge of scrolling. */
@Composable
internal fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AppLayout.ContentWidth,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) {
            content()
        }
    }
}
