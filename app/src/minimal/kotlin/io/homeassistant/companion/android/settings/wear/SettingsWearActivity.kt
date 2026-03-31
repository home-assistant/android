package io.homeassistant.companion.android.settings.wear

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class SettingsWearActivity : AppCompatActivity() {

    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearActivity::class.java)
        }
    }
}
