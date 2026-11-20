package io.homeassistant.companion.android.settings.url.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ChevronDown
import io.github.timoptr.mdiicons.generated.ChevronUp
import io.github.timoptr.mdiicons.generated.Delete
import io.github.timoptr.mdiicons.generated.Plus
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.settings.url.ExtraUrlError
import io.homeassistant.companion.android.settings.url.ExtraUrlsUiState
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlinx.coroutines.launch

/**
 * Edits the ordered list of extra URLs. The app tries them in this order, after the main URL,
 * when the preferred URLs are not reachable.
 */
@Composable
fun ExtraUrlsView(
    uiState: ExtraUrlsUiState,
    onAdd: suspend (String) -> Boolean,
    onRemove: (String) -> Unit,
    onMove: (url: String, offset: Int) -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val urls = uiState.urls
    Column(
        modifier = modifier
            .padding(safeBottomPaddingValues(applyHorizontal = false))
            .padding(vertical = HADimens.SPACE4),
    ) {
        Text(
            text = stringResource(commonR.string.pref_connection_extra_urls_summary),
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
            modifier = Modifier.padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
        )
        ExtraUrlInput(error = uiState.error, onAdd = onAdd, onInputChanged = onInputChanged)

        if (urls.isEmpty()) {
            Text(
                text = stringResource(commonR.string.extra_urls_empty),
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(urls, key = { it }) { url ->
                ExtraUrlRow(
                    url = url,
                    canMoveUp = url != urls.first(),
                    canMoveDown = url != urls.last(),
                    onMoveUp = { onMove(url, -1) },
                    onMoveDown = { onMove(url, 1) },
                    onRemove = { onRemove(url) },
                )
            }
        }
    }
}

@Composable
private fun ExtraUrlInput(error: ExtraUrlError?, onAdd: suspend (String) -> Boolean, onInputChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val submit: () -> Unit = {
        val submitted = input
        scope.launch {
            // Text typed while the URL was being saved belongs to the next URL and must not be cleared.
            if (onAdd(submitted) && input == submitted) input = ""
        }
    }

    HATextField(
        value = input,
        onValueChange = {
            input = it
            onInputChanged()
        },
        label = { Text(stringResource(commonR.string.input_url), style = HATextStyle.UserInput) },
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    stringResource(
                        when (it) {
                            ExtraUrlError.Invalid -> commonR.string.url_parse_error
                            ExtraUrlError.Duplicate -> commonR.string.extra_urls_duplicate
                        },
                    ),
                )
            }
        },
        trailingIcon = {
            HAIconButton(
                icon = Mdi.Plus.rememberImageVector(),
                onClick = submit,
                contentDescription = stringResource(commonR.string.extra_urls_add),
                enabled = input.isNotBlank(),
            )
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HADimens.SPACE4),
    )
}

@Composable
private fun ExtraUrlRow(
    url: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HADimens.SPACE4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
    ) {
        Text(
            text = url,
            style = HATextStyle.UserInput,
            modifier = Modifier
                .weight(1f)
                .padding(end = HADimens.SPACE2),
        )
        HAIconButton(
            icon = Mdi.ChevronUp.rememberImageVector(),
            onClick = onMoveUp,
            contentDescription = stringResource(commonR.string.extra_urls_move_up),
            variant = ButtonVariant.NEUTRAL,
            enabled = canMoveUp,
        )
        HAIconButton(
            icon = Mdi.ChevronDown.rememberImageVector(),
            onClick = onMoveDown,
            contentDescription = stringResource(commonR.string.extra_urls_move_down),
            variant = ButtonVariant.NEUTRAL,
            enabled = canMoveDown,
        )
        HAIconButton(
            icon = Mdi.Delete.rememberImageVector(),
            onClick = onRemove,
            contentDescription = stringResource(commonR.string.delete),
            variant = ButtonVariant.DANGER,
        )
    }
}

@Preview
@Composable
private fun PreviewExtraUrlsView() {
    HAThemeForPreview {
        ExtraUrlsView(
            uiState = ExtraUrlsUiState(urls = listOf("https://vpn.example.com/", "https://ddns.example.com:8123/")),
            onAdd = { true },
            onRemove = {},
            onMove = { _, _ -> },
            onInputChanged = {},
        )
    }
}

@Preview
@Composable
private fun PreviewExtraUrlsViewWithError() {
    HAThemeForPreview {
        ExtraUrlsView(
            uiState = ExtraUrlsUiState(error = ExtraUrlError.Duplicate),
            onAdd = { false },
            onRemove = {},
            onMove = { _, _ -> },
            onInputChanged = {},
        )
    }
}
