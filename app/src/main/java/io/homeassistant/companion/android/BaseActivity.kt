package io.homeassistant.companion.android

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.settings.language.LanguagesManagerProvider

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val manager = LanguagesManagerProvider().getManager(newBase)
        super.attachBaseContext(manager.getContextWrapper(newBase))
    }
}
