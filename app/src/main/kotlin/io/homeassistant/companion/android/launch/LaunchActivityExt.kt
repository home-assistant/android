package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent

internal fun Context.startLaunchOnboarding(urlToOnboard: String, hideExistingServers: Boolean, skipWelcome: Boolean) {
    startActivity(
        LaunchActivity.newInstance(
            this,
            LaunchActivity.DeepLink.OpenOnboarding(
                urlToOnboard,
                hideExistingServers = hideExistingServers,
                skipWelcome = skipWelcome,
            ),
        ),
    )
}

internal fun Context.startLaunchWithNavigateTo(path: String, serverId: Int) {
    startActivity(LaunchActivity.newInstance(this, LaunchActivity.DeepLink.NavigateTo(path, serverId)))
}

internal fun Context.intentLaunchWearOnboarding(wearName: String, urlToOnboard: String?): Intent {
    return LaunchActivity.newInstance(this, LaunchActivity.DeepLink.OpenWearOnboarding(wearName, urlToOnboard))
}

internal fun Context.intentLaunchOnboarding(
    urlToOnboard: String?,
    hideExistingServers: Boolean,
    skipWelcome: Boolean,
): Intent {
    return LaunchActivity.newInstance(
        this,
        LaunchActivity.DeepLink.OpenOnboarding(
            urlToOnboard,
            hideExistingServers = hideExistingServers,
            skipWelcome = skipWelcome,
        ),
    )
}
