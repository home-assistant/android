package io.homeassistant.companion.android.views

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.Text
import io.homeassistant.companion.android.common.R as commonR
import kotlin.math.floor

@Composable
fun ListHeader(@StringRes id: Int, modifier: Modifier = Modifier) {
    ListHeader(stringResource(id), modifier)
}

@Composable
fun ListHeader(string: String, modifier: Modifier = Modifier) {
    ListHeader {
        val maxLines = with(LocalDensity.current) {
            if (LocalTextStyle.current.fontSize.isSp) {
                floor(48 / LocalTextStyle.current.fontSize.toDp().value).toInt() // A ListHeader is 48dp
            } else {
                1 // Fallback as em cannot be converted
            }
        }
        Text(
            text = string,
            modifier = modifier,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun PreviewListHeader() {
    ListHeader(
        id = commonR.string.other,
    )
}
