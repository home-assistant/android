package io.homeassistant.companion.android.settings

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.settings.language.LanguagesManagerProvider

@Component(dependencies = [AppComponent::class])
interface SettingsComponent {

    fun inject(languageManagerProvider: LanguagesManagerProvider)
}
