package io.homeassistant.companion.android.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R

@Composable
fun getDescription(isChecked: Boolean): String {
    return if (isChecked) {
        stringResource(R.string.enabled)
    } else {
        stringResource(R.string.disabled)
    }
}
