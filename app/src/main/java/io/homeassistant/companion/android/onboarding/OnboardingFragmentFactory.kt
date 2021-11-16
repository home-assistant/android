package io.homeassistant.companion.android.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationFragment
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenter
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryPresenter
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenter
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupPresenter
import io.homeassistant.companion.android.themes.ThemesManager
import javax.inject.Inject

class OnboardingFragmentFactory @Inject constructor(
    private val authenticationPresenter: AuthenticationPresenter,
    private val themesManager: ThemesManager,
    private val discoveryPresenter: DiscoveryPresenter,
    private val mobileAppIntegrationPresenter: MobileAppIntegrationPresenter,
    private val manualSetupPresenter: ManualSetupPresenter
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return when (className) {
            AuthenticationFragment::class.java.name ->
                AuthenticationFragment(authenticationPresenter, themesManager)
            DiscoveryFragment::class.java.name ->
                DiscoveryFragment(discoveryPresenter)
            MobileAppIntegrationFragment::class.java.name ->
                MobileAppIntegrationFragment(mobileAppIntegrationPresenter)
            ManualSetupFragment::class.java.name ->
                ManualSetupFragment(manualSetupPresenter)
            else -> super.instantiate(classLoader, className)
        }
    }
}
