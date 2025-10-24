package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.util.FailFast

/**
 * This file is temporary and will be removed once the new launcher is available.
 */

internal fun Context.startLauncherOnboarding(urlToOnboard: String, hideExistingServers: Boolean, skipWelcome: Boolean) {
    FailFast.fail { "New Launcher is not available on release yet" }
}

internal fun Context.startLauncherWithNavigateTo(path: String, serverId: Int) {
    FailFast.fail { "New Launcher is not available on release yet" }
}

internal fun Context.intentLauncherWearOnboarding(wearName: String, serverUrl: String?): Intent {
    FailFast.fail { "New Launcher is not available on release yet" }
    return Intent()
}

internal fun Context.intentLauncherOnboarding(
    urlToOnboard: String?,
    hideExistingServers: Boolean,
    skipWelcome: Boolean,
): Intent {
    FailFast.fail { "New Launcher is not available on release yet" }
    return Intent()
}
