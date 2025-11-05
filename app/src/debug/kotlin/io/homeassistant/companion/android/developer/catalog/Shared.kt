package io.homeassistant.companion.android.developer.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

@Composable
fun CatalogRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        content()
    }
}

fun LazyListScope.catalogSection(title: String, content: @Composable () -> Unit) {
    item {
        Text(text = title, modifier = Modifier.padding(top = HADimens.SPACE4), style = HATextStyle.Body)
    }
    item {
        content()
    }
}
