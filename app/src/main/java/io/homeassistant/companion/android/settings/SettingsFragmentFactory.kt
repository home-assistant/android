package io.homeassistant.companion.android.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.language.LanguagesProvider
import io.homeassistant.companion.android.settings.log.LogFragment
import io.homeassistant.companion.android.settings.notification.NotificationDetailFragment
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.qs.ManageTilesFragment
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsSettingsFragment
import javax.inject.Inject

class SettingsFragmentFactory @Inject constructor(
    private val settingsPresenter: SettingsPresenter,
    private val languagesProvider: LanguagesProvider,
    private val integrationRepository: IntegrationRepository
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return when (className) {
            SettingsFragment::class.java.name -> SettingsFragment(settingsPresenter, languagesProvider)
            LogFragment::class.java.name -> LogFragment()
            NotificationDetailFragment::class.java.name -> NotificationDetailFragment()
            NotificationHistoryFragment::class.java.name -> NotificationHistoryFragment()
            ManageTilesFragment::class.java.name -> ManageTilesFragment(integrationRepository)
            ManageShortcutsSettingsFragment::class.java.name -> ManageShortcutsSettingsFragment()
            ManageWidgetsSettingsFragment::class.java.name -> ManageWidgetsSettingsFragment()
            else -> super.instantiate(classLoader, className)
        }
    }
}
