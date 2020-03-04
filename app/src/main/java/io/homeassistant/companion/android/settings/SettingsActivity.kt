package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.lokalise.sdk.LokaliseContextWrapper
import com.lokalise.sdk.menu_inflater.LokaliseMenuInflater
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.ssid.SsidDialogFragment
import io.homeassistant.companion.android.settings.ssid.SsidPreference

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {

    companion object {
        private const val SSID_DIALOG_TAG = "${BuildConfig.APPLICATION_ID}.SSID_DIALOG_TAG"

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
    }

    override fun onPreferenceDisplayDialog(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        if (pref is SsidPreference) {
            val ssidDialog = SsidDialogFragment.newInstance("connection_internal_ssids")
            ssidDialog.show(supportFragmentManager, SSID_DIALOG_TAG)
            return true
        }
        return false
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LokaliseContextWrapper.wrap(newBase))
    }

    override fun getMenuInflater(): MenuInflater {
        return LokaliseMenuInflater(this)
    }
}
