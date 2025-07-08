package io.homeassistant.companion.android.settings.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A Composable that displays a typical Material Design list subheader.
 *
 * @param paddingForIcon Whether the header's start padding should be aligned
 *        with a [SettingsRow] which has an icon.
 */
@Composable
fun SettingsSubheader(
    text: String,
    paddingForIcon: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .heightIn(min = 40.dp)
            .padding(start = if (paddingForIcon) 72.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
        )
    }
}

@Preview
@Composable
fun PreviewSettingsSubheadingDefault() {
    SettingsSubheader("Attributes")
}

@Preview
@Composable
fun PreviewSettingsSubheadingWithPadding() {
    SettingsSubheader("Health Connect sensors", paddingForIcon = true)
}
