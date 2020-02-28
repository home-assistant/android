package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuInflater
import androidx.appcompat.app.AppCompatActivity
import com.lokalise.sdk.LokaliseContextWrapper
import com.lokalise.sdk.menu_inflater.LokaliseMenuInflater
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
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, SettingsFragment.newInstance())
            .commit()
        val sharedPref: SharedPreferences = getSharedPreferences("lock", Context.MODE_PRIVATE)
        val prefEditor = sharedPref.edit()
        prefEditor.putBoolean("lock", false)
        prefEditor.apply()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LokaliseContextWrapper.wrap(newBase))
    }

    override fun getMenuInflater(): MenuInflater {
        return LokaliseMenuInflater(this)
    }
}
