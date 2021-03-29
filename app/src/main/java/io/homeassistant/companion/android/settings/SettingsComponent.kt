package io.homeassistant.companion.android.settings

import dagger.Component
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment

@Component(dependencies = [AppComponent::class])
interface SettingsComponent {

    fun inject(activity: BaseActivity)

    fun inject(fragment: ManageShortcutsSettingsFragment)
}
