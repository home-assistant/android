package io.homeassistant.companion.android.settings.wear.views

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class SettingsWearMainView : AppCompatActivity() {

    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearMainView::class.java)
        }
    }
}
