package io.homeassistant.companion.android.settings.url.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExternalUrlInputView(url: String?, focusRequester: FocusRequester, onSaveUrl: (String) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var urlInput by remember(url) { mutableStateOf(url) }
    var urlError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        TextField(
            value = urlInput ?: "",
            singleLine = true,
            onValueChange = {
                urlInput = it
                urlError = false
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    urlError = !performUrlUpdate(urlInput?.trim(), url, onSaveUrl)
                    if (!urlError) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                },
            ),
            placeholder = { Text(stringResource(commonR.string.input_url)) },
            isError = urlError,
            trailingIcon = if (urlError) {
                {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = stringResource(commonR.string.url_invalid),
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        if (urlError) {
            Text(
                text = stringResource(commonR.string.url_parse_error),
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        if (urlInput != url && urlInput?.trim()?.toHttpUrlOrNull()?.toString() != url) {
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    urlError = !performUrlUpdate(urlInput?.trim(), url, onSaveUrl)
                    if (!urlError) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                },
            ) {
                Text(stringResource(commonR.string.update))
            }
        }
    }
}

/**
 * Try saving the url with the value of the input.
 * @return boolean indicating if the url was saved successfully
 */
private fun performUrlUpdate(input: String?, current: String?, onSaveUrl: (String) -> Unit): Boolean {
    return if (input != current && input?.toHttpUrlOrNull()?.toString() != current) {
        val urlValue = input?.toHttpUrlOrNull()
        val isValid = urlValue != null
        if (isValid) {
            onSaveUrl(urlValue.toString())
        }
        isValid
    } else {
        true
    }
}
