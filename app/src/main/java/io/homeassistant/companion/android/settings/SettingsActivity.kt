package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R


class SettingsActivity : AppCompatActivity() {

    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<TextView>(R.id.version_text_view).text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

}
