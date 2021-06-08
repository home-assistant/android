package io.homeassistant.companion.android

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.settings.DaggerSettingsComponent
import io.homeassistant.companion.android.settings.language.LanguagesManager
import javax.inject.Inject

open class BaseActivity : AppCompatActivity() {

    @Inject
    lateinit var lm: LanguagesManager

    override fun attachBaseContext(newBase: Context) {

        DaggerSettingsComponent.builder()
            .appComponent((newBase.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        super.attachBaseContext(lm.getContextWrapper(newBase))
    }
}
