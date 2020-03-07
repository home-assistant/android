package io.homeassistant.companion.android

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.lock.LockActivity
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.SettingsFragment
import io.homeassistant.companion.android.settings.ssid.SsidDialogFragment
import io.homeassistant.companion.android.webview.WebViewActivity

@Component(dependencies = [AppComponent::class], modules = [PresenterModule::class])
interface PresenterComponent {

    fun inject(activity: LaunchActivity)

    fun inject(fragment: DiscoveryFragment)

    fun inject(fragment: AuthenticationFragment)

    fun inject(fragment: ManualSetupFragment)

    fun inject(fragment: MobileAppIntegrationFragment)

    fun inject(activity: SettingsActivity)

    fun inject(fragment: SettingsFragment)

    fun inject(activity: WebViewActivity)

    fun inject(activity: LockActivity)

    fun inject(dialog: SsidDialogFragment)
}
