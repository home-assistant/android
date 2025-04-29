package io.homeassistant.companion.android.util.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.glance.LocalContext

@Composable
@ReadOnlyComposable
fun glanceStringResource(@StringRes id: Int, vararg arguments: Any): String =
    LocalContext.current.getString(id, *arguments)
