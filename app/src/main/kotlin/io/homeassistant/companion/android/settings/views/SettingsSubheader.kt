package io.homeassistant.companion.android.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A Composable that displays a typical Material Design list subheader.
 *
 * @param textPadding Padding for the text, excluding padding above the line,
 *        which can be used to align with a [SettingsRow] which has an icon.
 */
@Composable
fun SettingsSubheader(
    text: String,
    modifier: Modifier = Modifier,
    textPadding: PaddingValues = SettingsSubheaderDefaults.TextOnlyRowPadding,
) {
    Text(
        text = text,
        modifier = modifier
            .padding(top = 8.dp)
            .heightIn(min = 40.dp)
            .padding(textPadding)
            .wrapContentSize(align = Alignment.CenterStart),
        style = MaterialTheme.typography.body2,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.primary,
    )
}

object SettingsSubheaderDefaults {
    val TextOnlyRowPadding = PaddingValues(horizontal = 16.dp)
    val TextWithIconRowPadding = PaddingValues(start = 72.dp, end = 16.dp)
}

@Preview
@Composable
private fun PreviewSettingsSubheadingDefault() {
    SettingsSubheader("Attributes")
}

@Preview
@Composable
private fun PreviewSettingsSubheadingWithModifiers() {
    SettingsSubheader(
        text = "Health Connect sensors",
        modifier = Modifier.background(Color.Red).width(400.dp),
        textPadding = SettingsSubheaderDefaults.TextWithIconRowPadding,
    )
}
