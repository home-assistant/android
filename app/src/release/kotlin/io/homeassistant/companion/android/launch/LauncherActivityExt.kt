package io.homeassistant.companion.android.launch

import android.content.Context
import io.homeassistant.companion.android.common.util.FailFast

/**
 * This file is temporary and will be removed once the new launcher is available.
 */

internal fun Context.startLauncherForInvite(serverToOnboard: String) {
    FailFast.fail { "New Launcher is not available on release yet" }
}

internal fun Context.startLauncherWithNavigateTo(path: String, serverId: Int) {
    FailFast.fail { "New Launcher is not available on release yet" }
}
