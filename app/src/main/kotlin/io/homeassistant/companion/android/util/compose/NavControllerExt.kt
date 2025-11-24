package io.homeassistant.companion.android.util.compose

import androidx.compose.ui.platform.AndroidUriHandler
import androidx.navigation.NavController

fun NavController.navigateToUri(uri: String) {
    AndroidUriHandler(context).openUri(uri)
}
