package com.example.myapplication.frontEnd.screens.campsiteViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

@Composable
internal fun CampsiteFormLayout(content: @Composable ColumnScope.() -> Unit) {
    AdaptiveContent(maxWidth = AppLayout.FormWidth) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** Gives two fields room for their labels when width or font scale makes a row impractical. */
@Composable
internal fun CampsiteFieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minPairWidth = 432.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        if (maxWidth >= minPairWidth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayout.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}
