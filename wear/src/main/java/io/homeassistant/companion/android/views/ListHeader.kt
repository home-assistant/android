package io.homeassistant.companion.android.views

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun ListHeader(@StringRes id: Int, modifier: Modifier = Modifier) {
    ListHeader(stringResource(id), modifier)
}

@Composable
fun ListHeader(string: String, modifier: Modifier = Modifier) {
    ListHeader {
        Row {
            Text(
                text = string,
                modifier = modifier
            )
        }
    }
}

@Preview
@Composable
private fun PreviewListHeader() {
    ListHeader(
        id = commonR.string.other
    )
}
