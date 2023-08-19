package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.fragment.app.commit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.qs.ManageTilesFragment
import io.homeassistant.companion.android.settings.sensor.SensorDetailFragment
import io.homeassistant.companion.android.settings.server.ServerSettingsFragment
import io.homeassistant.companion.android.settings.websocket.WebsocketSettingFragment
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    private lateinit var authenticator: Authenticator
    private lateinit var blurView: BlurView

    private var authenticating = false
    private var externalAuthCallback: ((Int) -> Boolean)? = null

    companion object {
        private const val TAG = "SettingsActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val entryPoint = EntryPointAccessors.fromActivity(this, SettingsFragmentFactoryEntryPoint::class.java)
        supportFragmentManager.fragmentFactory = entryPoint.getSettingsFragmentFactory()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        blurView = findViewById(R.id.blurView)
        blurView.setupWith(window.decorView.rootView as ViewGroup)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurAutoUpdate(true)
            .setBlurRadius(8f)
            .setHasFixedTransformationMatrix(false)
            .setBlurEnabled(false)

        authenticator = Authenticator(this, this, ::settingsActivityAuthenticationResult)

        if (savedInstanceState == null) {
            val settingsNavigation = intent.getStringExtra("fragment")
            supportFragmentManager.commit {
                replace(
                    R.id.content,
                    when {
                        settingsNavigation == "websocket" ->
                            if (serverManager.defaultServers.size == 1) {
                                WebsocketSettingFragment::class.java
                            } else {
                                SettingsFragment::class.java
                            }

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
                    } else if (settingsNavigation == "websocket") {
                        val servers = serverManager.defaultServers
                        if (servers.size == 1) {
                            Bundle().apply { putInt(WebsocketSettingFragment.EXTRA_SERVER, servers[0].id) }
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        setAppActive(false)
    }

    override fun onPause() {
        super.onPause()
        setAppActive(false)
    }

    override fun onResume() {
        super.onResume()
        blurView.setBlurEnabled(isAppLocked())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            if (isAppLocked()) {
                authenticating = true
                authenticator.authenticate(getString(commonR.string.biometric_title))
                blurView.setBlurEnabled(true)
            } else {
                setAppActive(true)
                blurView.setBlurEnabled(false)
            }
        }
    }

    private fun settingsActivityAuthenticationResult(result: Int) {
        val isExtAuth = (externalAuthCallback != null)
        Log.d(TAG, "settingsActivityAuthenticationResult(): authenticating: $authenticating, externalAuth: $isExtAuth")

        externalAuthCallback?.let {
            if (it(result)) {
                externalAuthCallback = null
            }
        }

        if (authenticating) {
            authenticating = false
            when (result) {
                Authenticator.SUCCESS -> {
                    Log.d(TAG, "Authentication successful, unlocking app")
                    blurView.setBlurEnabled(false)
                    setAppActive(true)
                }
                Authenticator.CANCELED -> {
                    Log.d(TAG, "Authentication canceled by user, closing activity")
                    finishAffinity()
                }
                else -> Log.d(TAG, "Authentication failed, retry attempts allowed")
            }
        }
    }

    /**
     * @return `true` if the app is locked for the active server or the currently visible server
     */
    private fun isAppLocked(): Boolean {
        val serverFragment = supportFragmentManager.findFragmentByTag(ServerSettingsFragment.TAG)
        val serverLocked = serverFragment?.let { isAppLocked((it as ServerSettingsFragment).getServerId()) } ?: false
        return serverLocked || isAppLocked(ServerManager.SERVER_ID_ACTIVE)
    }

    fun isAppLocked(serverId: Int?): Boolean = runBlocking {
        serverManager.getServer(serverId ?: ServerManager.SERVER_ID_ACTIVE)?.let {
            try {
                serverManager.integrationRepository(it.id).isAppLocked()
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Cannot determine app locked state")
                false
            }
        } ?: false
    }

    /**
     * Set the app active for the currently active server, and the currently visible server if
     * different
     */
    private fun setAppActive(active: Boolean) {
        val serverFragment = supportFragmentManager.findFragmentByTag(ServerSettingsFragment.TAG)
        serverFragment?.let { setAppActive((it as ServerSettingsFragment).getServerId(), active) }
        setAppActive(ServerManager.SERVER_ID_ACTIVE, active)
    }

    fun setAppActive(serverId: Int?, active: Boolean) = runBlocking {
        serverManager.getServer(serverId ?: ServerManager.SERVER_ID_ACTIVE)?.let {
            try {
                serverManager.integrationRepository(it.id).setAppActive(active)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Cannot set app active $active for server $serverId")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun requestAuthentication(title: String, callback: (Int) -> Boolean): Boolean {
        return if (BiometricManager.from(this).canAuthenticate(Authenticator.AUTH_TYPES) != BiometricManager.BIOMETRIC_SUCCESS) {
            false
        } else {
            externalAuthCallback = callback
            authenticator.authenticate(title)

            true
        }
    }

    /** Used to inject classes before [onCreate] */
    @EntryPoint
    @InstallIn(ActivityComponent::class)
    interface SettingsFragmentFactoryEntryPoint {
        fun getSettingsFragmentFactory(): SettingsFragmentFactory
    }
}
