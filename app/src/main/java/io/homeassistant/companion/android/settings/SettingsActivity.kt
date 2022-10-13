package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.biometric.BiometricManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.qs.ManageTilesFragment
import io.homeassistant.companion.android.settings.sensor.SensorDetailFragment
import io.homeassistant.companion.android.settings.websocket.WebsocketSettingFragment
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private lateinit var authenticator: Authenticator

    private var authenticating = false
    private var externalAuthCallback: ((Int) -> Boolean)? = null

    companion object {
        private const val TAG = "SettingsActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_settings, menu)

        (menu.findItem(R.id.action_search)?.actionView as SearchView).apply {
            queryHint = getString(commonR.string.search_sensors)
            maxWidth = Integer.MAX_VALUE
        }

        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val entryPoint = EntryPointAccessors.fromActivity(this, SettingsFragmentFactoryEntryPoint::class.java)
        supportFragmentManager.fragmentFactory = entryPoint.getSettingsFragmentFactory()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        authenticator = Authenticator(this, this, ::settingsActivityAuthenticationResult)

        if (savedInstanceState == null) {
            val settingsNavigation = intent.getStringExtra("fragment")
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.content,
                    when {
                        settingsNavigation == "websocket" -> WebsocketSettingFragment::class.java
                        settingsNavigation == "notification_history" -> NotificationHistoryFragment::class.java
                        settingsNavigation?.startsWith("sensors/") == true -> SensorDetailFragment::class.java
                        settingsNavigation?.startsWith("tiles/") == true -> ManageTilesFragment::class.java
                        else -> SettingsFragment::class.java
                    },
                    if (settingsNavigation?.startsWith("sensors/") == true) {
                        val sensorId = settingsNavigation.split("/")[1]
                        SensorDetailFragment.newInstance(sensorId).arguments
                    } else if (settingsNavigation?.startsWith("tiles/") == true) {
                        val tileId = settingsNavigation.split("/")[1]
                        Bundle().apply { putString("id", tileId) }
                    } else null
                )
                .commit()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        runBlocking {
            integrationUseCase.setAppActive(false)
        }
    }

    override fun onPause() {
        super.onPause()
        runBlocking {
            integrationUseCase.setAppActive(false)
        }
    }

    override fun onResume() {
        super.onResume()
        runBlocking {
            val appLocked = integrationUseCase.isAppLocked()
            Log.d(TAG, "onResume(): appLock: " + appLocked)

            if (appLocked) {
                authenticating = true
                authenticator.authenticate(getString(commonR.string.biometric_title))
            }
        }
    }

    private fun settingsActivityAuthenticationResult(result: Int) {
        val isExtAuth = (externalAuthCallback != null)
        Log.d(TAG, "settingsActivityAuthenticationResult(): authenticating: " + authenticating + ", externalAuth: " + isExtAuth)

        externalAuthCallback?.let {
            when (it(result)) {
                true -> {
                    externalAuthCallback = null
                }
            }
        }

        if (authenticating) {
            authenticating = false
            when (result) {
                Authenticator.SUCCESS -> {
                    Log.d(TAG, "Authentication successful, unlocking app")
                    runBlocking {
                        integrationUseCase.setAppActive(true)
                    }
                }
                Authenticator.CANCELED -> {
                    Log.d(TAG, "Authentication canceled by user, closing activity")
                    finishAffinity()
                }
                else -> Log.d(TAG, "Authentication failed, retry attempts allowed")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    onBackPressed()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun requestAuthentication(title: String, callback: (Int) -> Boolean): Boolean {
        if (BiometricManager.from(this).canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS) {
            return false
        } else {
            externalAuthCallback = callback
            authenticator.authenticate(title)

            return true
        }
    }

    @EntryPoint
    @InstallIn(ActivityComponent::class)
    interface SettingsFragmentFactoryEntryPoint {
        fun getSettingsFragmentFactory(): SettingsFragmentFactory
    }
}
