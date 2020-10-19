package io.homeassistant.companion.android.settings.language

import android.content.Context
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.settings.DaggerSettingsComponent
import javax.inject.Inject

class LanguagesManagerProvider {

    @Inject
    lateinit var lm: LanguagesManager

    fun getManager(context: Context): LanguagesManager {
        DaggerSettingsComponent.builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        return lm
    }
}
