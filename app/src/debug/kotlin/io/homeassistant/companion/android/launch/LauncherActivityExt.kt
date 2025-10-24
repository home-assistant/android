package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.launcher.LauncherActivity

/**
 * This file is temporary and will be removed once the new launcher is available.
 */

internal fun Context.startLauncherOnboarding(urlToOnboard: String, hideExistingServers: Boolean, skipWelcome: Boolean) {
    startActivity(
        LauncherActivity.newInstance(
            this,
            LauncherActivity.DeepLink.OpenOnboarding(
                urlToOnboard,
                hideExistingServers = hideExistingServers,
                skipWelcome = skipWelcome,
            ),
        ),
    )
}

internal fun Context.startLauncherWithNavigateTo(path: String, serverId: Int) {
    startActivity(LauncherActivity.newInstance(this, LauncherActivity.DeepLink.NavigateTo(path, serverId)))
}

internal fun Context.intentLauncherWearOnboarding(wearName: String, urlToOnboard: String?): Intent {
    return LauncherActivity.newInstance(this, LauncherActivity.DeepLink.OpenWearOnboarding(wearName, urlToOnboard))
}

internal fun Context.intentLauncherOnboarding(
    urlToOnboard: String?,
    hideExistingServers: Boolean,
    skipWelcome: Boolean,
): Intent {
    return LauncherActivity.newInstance(
        this,
        LauncherActivity.DeepLink.OpenOnboarding(
            urlToOnboard,
            hideExistingServers = hideExistingServers,
            skipWelcome = skipWelcome,
        ),
    )
}
