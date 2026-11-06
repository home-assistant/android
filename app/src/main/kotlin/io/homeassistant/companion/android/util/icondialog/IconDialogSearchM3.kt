package io.homeassistant.companion.android.util.icondialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.homeassistant.companion.android.common.compose.composable.HATextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

@Composable
fun IconDialogSearchM3(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnglish = Locale.current.language == "en"
    HATextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = {
            Text(
                text = stringResource(
                    if (isEnglish) R.string.search_icons else R.string.search_icons_in_english,
                ),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (value.isNotBlank()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Preview
@Composable
private fun IconDialogSearchM3Preview() {
    HAThemeForPreview {
        Surface {
            IconDialogSearchM3(
                value = "account",
                onValueChange = {},
            )
        }
    }
}
