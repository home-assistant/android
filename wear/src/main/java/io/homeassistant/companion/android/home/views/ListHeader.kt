package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun ListHeader(
    stringId: Int,
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit
) {
    ListHeader(stringResource(stringId), expanded, onExpandChanged)
}

@Composable
fun ListHeader(
    string: String,
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit
) {
    ListHeader(
        modifier = Modifier
            .clickable { onExpandChanged(!expanded) }
    ) {
        Row {
            Text(
                text = string + if (expanded) "\u2001-" else "\u2001+"
            )
        }
    }
}

@Composable
fun ListHeader(id: Int, modifier: Modifier = Modifier) {
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
        stringId = commonR.string.other,
        expanded = true,
        onExpandChanged = {}
    )
}
