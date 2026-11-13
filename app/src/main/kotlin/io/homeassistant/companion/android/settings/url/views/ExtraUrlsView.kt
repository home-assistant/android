package io.homeassistant.companion.android.settings.url.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ChevronDown
import io.github.timoptr.mdiicons.generated.ChevronUp
import io.github.timoptr.mdiicons.generated.Delete
import io.github.timoptr.mdiicons.generated.Plus
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.url.ExtraUrlError
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlinx.coroutines.launch

/**
 * Edits the ordered list of extra URLs. The app tries them in this order, after the main URL,
 * when the preferred URLs are not reachable.
 */
@Composable
fun ExtraUrlsView(
    urls: List<String>,
    error: ExtraUrlError?,
    onAdd: suspend (String) -> Boolean,
    onRemove: (Int) -> Unit,
    onMove: (index: Int, offset: Int) -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(safeBottomPaddingValues(applyHorizontal = false))
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(commonR.string.pref_connection_extra_urls_summary),
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ExtraUrlInput(error = error, onAdd = onAdd, onInputChanged = onInputChanged)

        if (urls.isEmpty()) {
            Text(
                text = stringResource(commonR.string.extra_urls_empty),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(urls, key = { _, url -> url }) { index, url ->
                ExtraUrlRow(
                    url = url,
                    canMoveUp = index > 0,
                    canMoveDown = index < urls.lastIndex,
                    onMoveUp = { onMove(index, -1) },
                    onMoveDown = { onMove(index, 1) },
                    onRemove = { onRemove(index) },
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
        scope.launch {
            if (onAdd(input)) input = ""
        }
    }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = input,
            onValueChange = {
                input = it
                onInputChanged()
            },
            singleLine = true,
            isError = error != null,
            placeholder = { Text(stringResource(commonR.string.input_url)) },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = input.isNotBlank()) {
            Icon(
                imageVector = Mdi.Plus.rememberImageVector(),
                contentDescription = stringResource(commonR.string.extra_urls_add),
            )
        }
    }
    error?.let {
        Text(
            text = stringResource(
                when (it) {
                    ExtraUrlError.Invalid -> commonR.string.url_parse_error
                    ExtraUrlError.Duplicate -> commonR.string.extra_urls_duplicate
                },
            ),
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )
    }
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = url, modifier = Modifier.weight(1f).padding(end = 8.dp))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Mdi.ChevronUp.rememberImageVector(),
                contentDescription = stringResource(commonR.string.extra_urls_move_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Mdi.ChevronDown.rememberImageVector(),
                contentDescription = stringResource(commonR.string.extra_urls_move_down),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Mdi.Delete.rememberImageVector(),
                contentDescription = stringResource(commonR.string.delete),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewExtraUrlsView() {
    ExtraUrlsView(
        urls = listOf("https://vpn.example.com/", "https://ddns.example.com:8123/"),
        error = null,
        onAdd = { true },
        onRemove = {},
        onMove = { _, _ -> },
        onInputChanged = {},
    )
}
