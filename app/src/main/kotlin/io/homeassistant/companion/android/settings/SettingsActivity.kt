package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.core.content.IntentCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import eightbitlab.com.blurview.BlurView
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.settings.developer.DeveloperSettingsFragment
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.qs.ManageTilesFragment
import io.homeassistant.companion.android.settings.sensor.SensorDetailFragment
import io.homeassistant.companion.android.settings.server.ServerSettingsFragment
import io.homeassistant.companion.android.settings.websocket.WebsocketSettingFragment
import io.homeassistant.companion.android.util.applySafeDrawingInsets
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import timber.log.Timber

private const val EXTRA_FRAGMENT = "fragment"

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    private lateinit var authenticator: Authenticator
    private lateinit var blurView: BlurView

    private var authenticating = false
    private var externalAuthCallback: ((Int) -> Boolean)? = null

    companion object {
        fun newInstance(context: Context, screen: Deeplink? = null): Intent {
            return Intent(context, SettingsActivity::class.java).apply {
                if (screen != null) {
                    putExtra(EXTRA_FRAGMENT, screen)
                }
            }
        }
    }

    @Parcelize
    sealed interface Deeplink : Parcelable {
        data object Developer : Deeplink
        data object NotificationHistory : Deeplink
        data class QSTile(val tileId: String) : Deeplink
        data class Sensor(val sensorId: String) : Deeplink
        data object Websocket : Deeplink
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val entryPoint = EntryPointAccessors.fromActivity(this, SettingsFragmentFactoryEntryPoint::class.java)
        supportFragmentManager.fragmentFactory = entryPoint.getSettingsFragmentFactory()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Delegate bottom insets to the fragments
        findViewById<View>(R.id.root).applySafeDrawingInsets(applyBottom = false, consumeInsets = false)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        blurView = findViewById(R.id.blurView)
        blurView.setupWith(window.decorView.rootView as ViewGroup)
            .setBlurRadius(8f)
            .setBlurEnabled(false)

        authenticator = Authenticator(this, this, ::settingsActivityAuthenticationResult)

        if (savedInstanceState == null) {
            val settingsNavigation = IntentCompat.getParcelableExtra(intent, EXTRA_FRAGMENT, Deeplink::class.java)
            supportFragmentManager.commit {
                replace(
                    R.id.content,
                    when (settingsNavigation) {
                        Deeplink.Websocket -> if (serverManager.defaultServers.size == 1) {
                            WebsocketSettingFragment::class.java
                        } else {
                            SettingsFragment::class.java
                        }
                        Deeplink.Developer -> DeveloperSettingsFragment::class.java
                        Deeplink.NotificationHistory -> NotificationHistoryFragment::class.java
                        is Deeplink.Sensor -> SensorDetailFragment::class.java
                        is Deeplink.QSTile -> ManageTilesFragment::class.java
                        else -> SettingsFragment::class.java
                    },
                    when (settingsNavigation) {
                        is Deeplink.Sensor -> {
                            SensorDetailFragment.newInstance(settingsNavigation.sensorId).arguments
                        }

                        is Deeplink.QSTile -> {
                            val tileId = settingsNavigation.tileId
                            Bundle().apply { putString("id", tileId) }
                        }

                        Deeplink.Websocket -> {
                            val servers = serverManager.defaultServers
                            if (servers.size == 1) {
                                Bundle().apply { putInt(WebsocketSettingFragment.EXTRA_SERVER, servers[0].id) }
                            } else {
                                null
                            }
                        }

                        else -> {
                            null
                        }
                    },
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
        lifecycleScope.launch {
            blurView.setBlurEnabled(isAppLocked())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            lifecycleScope.launch {
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
    }

    private fun settingsActivityAuthenticationResult(result: Int) {
        val isExtAuth = (externalAuthCallback != null)
        Timber.d("settingsActivityAuthenticationResult(): authenticating: $authenticating, externalAuth: $isExtAuth")

        externalAuthCallback?.let {
            if (it(result)) {
                externalAuthCallback = null
            }
        }

        if (authenticating) {
            authenticating = false
            when (result) {
                Authenticator.SUCCESS -> {
                    Timber.d("Authentication successful, unlocking app")
                    blurView.setBlurEnabled(false)
                    setAppActive(true)
                }
                Authenticator.CANCELED -> {
                    Timber.d("Authentication canceled by user, closing activity")
                    finishAffinity()
                }
                else -> Timber.d("Authentication failed, retry attempts allowed")
            }
        }
    }

    /**
     * @return `true` if the app is locked for the active server or the currently visible server
     */
    private suspend fun isAppLocked(): Boolean {
        val serverFragment = supportFragmentManager.findFragmentByTag(ServerSettingsFragment.TAG)
        val serverLocked = serverFragment?.let { isAppLocked((it as ServerSettingsFragment).getServerId()) } ?: false
        return serverLocked || isAppLocked(ServerManager.SERVER_ID_ACTIVE)
    }

    suspend fun isAppLocked(serverId: Int?): Boolean {
        return serverManager.getServer(serverId ?: ServerManager.SERVER_ID_ACTIVE)?.let {
            try {
                serverManager.integrationRepository(it.id).isAppLocked()
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Cannot determine app locked state")
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

    // TODO remove runBlocking https://github.com/home-assistant/android/issues/5688
    fun setAppActive(serverId: Int?, active: Boolean) = runBlocking {
        serverManager.getServer(serverId ?: ServerManager.SERVER_ID_ACTIVE)?.let {
            try {
                serverManager.integrationRepository(it.id).setAppActive(active)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Cannot set app active $active for server $serverId")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun requestAuthentication(title: String, callback: (Int) -> Boolean): Boolean {
        return if (BiometricManager.from(this).canAuthenticate(Authenticator.AUTH_TYPES) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
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
