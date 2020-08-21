package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.ssid.SsidDialogFragment
import io.homeassistant.companion.android.settings.ssid.SsidPreference
import io.homeassistant.companion.android.webview.WebViewActivity

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onPreferenceDisplayDialog(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        if (pref is SsidPreference) {
            val ssidDialog = SsidDialogFragment.newInstance("connection_internal_ssids")
            ssidDialog.show(supportFragmentManager, SSID_DIALOG_TAG)
            return true
        }
        return false
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            // Start intent and bring this activity to the top of the task
            // which reloads the webview
            // Without the webview doesn't reload and the sidebar will be shown from the
            // previous app configuration call

            // FLAG_ACTIVITY_CLEAR_TOP:
            // If set, and the activity being launched is already running in the current task,
            // then instead of launching a new instance of that activity, all of the other
            // activities on top of it will be closed and this Intent will be delivered
            // to the (now on top) old activity as a new Intent.
            val webViewActivityIntent = Intent(this, WebViewActivity::class.java)
            webViewActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(webViewActivityIntent)
        }
    }
}
