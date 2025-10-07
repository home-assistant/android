package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.launcher.LauncherActivity

/**
 * This file is temporary and will be removed once the new launcher is available.
 */

internal fun Context.startLauncherOnboarding(serverToOnboard: String, hideExistingServer: Boolean) {
    startActivity(
        LauncherActivity.newInstance(
            this,
            LauncherActivity.DeepLink.OpenOnboarding(serverToOnboard, hideExistingServer),
        ),
    )
}

internal fun Context.startLauncherWithNavigateTo(path: String, serverId: Int) {
    startActivity(LauncherActivity.newInstance(this, LauncherActivity.DeepLink.NavigateTo(path, serverId)))
}

internal fun Context.intentLauncherWearOnboarding(wearName: String, serverUrl: String?): Intent {
    return LauncherActivity.newInstance(this, LauncherActivity.DeepLink.OpenWearOnboarding(wearName, serverUrl))
}

internal fun Context.intentLauncherOnboarding(url: String?, hideExistingServer: Boolean): Intent {
    return LauncherActivity.newInstance(this, LauncherActivity.DeepLink.OpenOnboarding(url, hideExistingServer))
}
