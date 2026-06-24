package io.homeassistant.companion.android.launch

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget

internal fun Context.startLaunchInvitation(serverUrl: String) {
    startActivity(
        LaunchActivity.newInstance(
            this,
            LaunchActivity.DeepLink.OpenInvitation(serverUrl),
        ),
    )
}

internal fun Context.intentLaunchWithNavigateTo(target: FrontendTarget, serverId: Int): Intent =
    LaunchActivity.newInstance(this, LaunchActivity.DeepLink.NavigateTo(target, serverId))

internal fun Context.startLaunchWithNavigateTo(target: FrontendTarget, serverId: Int) {
    startActivity(intentLaunchWithNavigateTo(target, serverId))
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
