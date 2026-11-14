package io.homeassistant.companion.android.settings.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.DEFAULT_REQUEST_TIMEOUT
import io.homeassistant.companion.android.common.data.REQUEST_TIMEOUT_OPTIONS
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlin.time.Duration

@Composable
internal fun RequestTimeoutScreen(viewModel: RequestTimeoutViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RequestTimeoutContent(state = state, onSelect = viewModel::selectTimeout, onRetry = viewModel::load)
}

@Composable
internal fun RequestTimeoutContent(
    state: RequestTimeoutUiState,
    onSelect: (Duration) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = requestTimeoutOptions(enabled = !state.isSaving)
    LazyColumn(
        modifier = modifier.padding(horizontal = HADimens.SPACE4).padding(top = HADimens.SPACE4),
        contentPadding = safeBottomPaddingValues(applyHorizontal = false),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        item {
            Text(
                text = stringResource(commonR.string.network_request_timeout_description),
                style = HATextStyle.Body,
                textAlign = TextAlign.Start,
            )
        }
        item {
            if (state.timeout != null) {
                HARadioGroup(options = options, selectionKey = state.timeout, onSelect = { onSelect(it.selectionKey) })
            } else if (!state.hasError) {
                HALoading()
            }
        }
        if (state.hasError) {
            item {
                RequestTimeoutError()
            }
            if (state.timeout == null) {
                item { HAFilledButton(text = stringResource(commonR.string.retry), onClick = onRetry) }
            }
        }
    }
}

@Composable
private fun requestTimeoutOptions(enabled: Boolean): List<RadioOption<Duration>> {
    val resources = LocalResources.current
    val defaultLabel = stringResource(commonR.string.network_request_timeout_default)
    return remember(resources, defaultLabel, enabled) {
        REQUEST_TIMEOUT_OPTIONS.map { timeout ->
            RadioOption(
                selectionKey = timeout,
                headline = resources.getString(commonR.string.network_request_timeout_seconds, timeout.inWholeSeconds),
                secondary = defaultLabel.takeIf { timeout == DEFAULT_REQUEST_TIMEOUT },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun RequestTimeoutError() {
    Text(
        text = stringResource(commonR.string.network_request_timeout_error),
        color = LocalHAColorScheme.current.colorOnDangerQuiet,
        style = HATextStyle.Body,
        textAlign = TextAlign.Start,
    )
}

@Preview
@Composable
private fun RequestTimeoutPreview() {
    HAThemeForPreview {
        RequestTimeoutContent(
            state = RequestTimeoutUiState(timeout = DEFAULT_REQUEST_TIMEOUT),
            onSelect = {},
            onRetry = {},
        )
    }
}
