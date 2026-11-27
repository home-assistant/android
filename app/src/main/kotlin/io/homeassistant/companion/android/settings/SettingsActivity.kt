package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.core.content.IntentCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.authenticator.Authenticator.Companion.AuthenticationResult
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.settings.assist.AssistSettingsFragment
import io.homeassistant.companion.android.settings.developer.DeveloperSettingsFragment
import io.homeassistant.companion.android.settings.gestures.GesturesFragment
import io.homeassistant.companion.android.settings.mediacontrols.MediaControlsSettingsFragment
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment
import io.homeassistant.companion.android.settings.qs.ManageTilesFragment
import io.homeassistant.companion.android.settings.sensor.SensorDetailFragment
import io.homeassistant.companion.android.settings.server.ServerSettingsFragment
import io.homeassistant.companion.android.settings.ssid.SsidFragment
import io.homeassistant.companion.android.settings.websocket.WebsocketSettingFragment
import io.homeassistant.companion.android.util.applySafeDrawingInsets
import io.homeassistant.companion.android.util.setRecentsScreenshotProtection
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

private const val EXTRA_FRAGMENT = "fragment"

/**
 * Keeps the [settings] out of reach while blocked: [blurView] takes their touches, and they lose the focus, which keys
 * act on, and are hidden from accessibility services, which act without touches.
 */
private class SettingsBlock(private val blurView: BlurView, private val settings: ViewGroup) {

    /** The view that had the focus when the settings were blocked: it gets the focus back once they are unblocked. */
    private var focusBeforeBlock: View? = null

    init {
        // Only takes touches, nothing for accessibility services to act on
        blurView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Blocks or unblocks the settings, and blurs them alike unless [keepBlur], which leaves the blur as it is. */
    fun update(blocked: Boolean, keepBlur: Boolean = false) {
        if (!keepBlur) blurView.setBlurEnabled(blocked)
        settings.importantForAccessibility = if (blocked) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        settings.descendantFocusability = if (blocked) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        if (blocked) {
            settings.findFocus()?.let { focused ->
                focusBeforeBlock = focused
                focused.clearFocus()
            }
        } else {
            focusBeforeBlock?.takeIf { it.isAttachedToWindow }?.requestFocus()
            focusBeforeBlock = null
        }
        blurView.isClickable = blocked
    }
}

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var authenticator: Authenticator
    private lateinit var settingsBlock: SettingsBlock

    private var authenticating = false

    /** How often the settings left the foreground: a lock check that ends after they left must not unblock them. */
    private var pauses = 0
    private var externalAuthCallback: ((AuthenticationResult) -> Boolean)? = null

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
        data class HomeNetwork(val serverId: Int) : Deeplink
        data object NotificationHistory : Deeplink

        /** Opens the tile settings, preselecting [tileId] when one is given. */
        data class QSTile(val tileId: String? = null) : Deeplink
        data class Sensor(val sensorId: String) : Deeplink
        data object Websocket : Deeplink
        data object AssistSettings : Deeplink
        data object Gestures : Deeplink
        data object MediaControls : Deeplink
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

        val blurTarget = findViewById<BlurTarget>(R.id.blurTarget)
        val blurView = findViewById<BlurView>(R.id.blurView)
        blurView.setupWith(blurTarget)
            .setFrameClearDrawable(window.decorView.background)
            .setBlurRadius(8f)
            .setBlurEnabled(false)
        // While the app is locked and until the lock was checked: the blur alone lets touches through
        settingsBlock = SettingsBlock(blurView, blurTarget)
        settingsBlock.update(blocked = true, keepBlur = true)

        authenticator = Authenticator(this, ::settingsActivityAuthenticationResult)

        if (savedInstanceState == null) {
            val settingsNavigation = IntentCompat.getParcelableExtra(intent, EXTRA_FRAGMENT, Deeplink::class.java)
            lifecycleScope.launch {
                supportFragmentManager.commit {
                    replace(
                        R.id.content,
                        when (settingsNavigation) {
                            Deeplink.Websocket -> if (serverManager.servers().size == 1) {
                                WebsocketSettingFragment::class.java
                            } else {
                                SettingsFragment::class.java
                            }
                            Deeplink.Developer -> DeveloperSettingsFragment::class.java
                            is Deeplink.HomeNetwork -> SsidFragment::class.java
                            Deeplink.NotificationHistory -> NotificationHistoryFragment::class.java
                            is Deeplink.Sensor -> SensorDetailFragment::class.java
                            is Deeplink.QSTile -> ManageTilesFragment::class.java
                            Deeplink.AssistSettings -> AssistSettingsFragment::class.java
                            Deeplink.Gestures -> GesturesFragment::class.java
                            Deeplink.MediaControls -> MediaControlsSettingsFragment::class.java
                            null -> SettingsFragment::class.java
                        },
                        when (settingsNavigation) {
                            is Deeplink.HomeNetwork -> {
                                Bundle().apply { putInt(SsidFragment.EXTRA_SERVER, settingsNavigation.serverId) }
                            }

                            is Deeplink.Sensor -> {
                                SensorDetailFragment.newInstance(settingsNavigation.sensorId).arguments
                            }

                            is Deeplink.QSTile -> {
                                val tileId = settingsNavigation.tileId
                                Bundle().apply { putString("id", tileId) }
                            }

                            Deeplink.Websocket -> {
                                val servers = serverManager.servers()
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
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        setAppActive(false)
    }

    override fun onPause() {
        super.onPause()
        setAppActive(false)
        pauses++
        // Until the lock is checked again
        settingsBlock.update(blocked = true, keepBlur = true)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val pausesBefore = pauses
            setRecentsScreenshotProtection(isAppLockEnabled())
            val locked = isAppLocked()
            if (!locked && pauses != pausesBefore) return@launch
            settingsBlock.update(blocked = locked)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            lifecycleScope.launch {
                val pausesBefore = pauses
                setRecentsScreenshotProtection(isAppLockEnabled())
                if (isAppLocked()) {
                    authenticating = true
                    authenticator.authenticate(getString(commonR.string.biometric_title))
                    settingsBlock.update(blocked = true)
                } else {
                    // Left before the check ended: the app must not be marked active, nor the settings unblocked
                    if (pauses != pausesBefore) return@launch
                    setAppActive(true)
                    settingsBlock.update(blocked = false)
                }
            }
        }
    }

    private fun settingsActivityAuthenticationResult(result: AuthenticationResult) {
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
                AuthenticationResult.SUCCESS -> {
                    Timber.d("Authentication successful, unlocking app")
                    settingsBlock.update(blocked = false)
                    setAppActive(true)
                }
                AuthenticationResult.CANCELED -> {
                    Timber.d("Authentication canceled by user, closing activity")
                    finishAffinity()
                }
                AuthenticationResult.ERROR -> Timber.d("Authentication failed, retry attempts allowed")
            }
        }
    }

    /**
     * @return `true` if the app is locked for the active server or the currently visible server
     */
    private suspend fun isAppLocked(): Boolean {
        val serverFragment = supportFragmentManager.findFragmentByTag(ServerSettingsFragment.TAG)
        val serverLocked = serverFragment?.let {
            viewModel.isAppLocked((it as ServerSettingsFragment).getServerId())
        } ?: false
        return serverLocked || viewModel.isAppLocked(ServerManager.SERVER_ID_ACTIVE)
    }

    /**
     * @return `true` if app lock is enabled for the active server or the currently visible server
     */
    private suspend fun isAppLockEnabled(): Boolean {
        val serverFragment = supportFragmentManager.findFragmentByTag(ServerSettingsFragment.TAG)
        val serverEnabled = serverFragment?.let {
            viewModel.isAppLockEnabled((it as ServerSettingsFragment).getServerId())
        } ?: false
        return serverEnabled || viewModel.isAppLockEnabled(ServerManager.SERVER_ID_ACTIVE)
    }

    /**
     * Set the app active for the currently active server, and the currently visible server if
     * different
     */
    private fun setAppActive(active: Boolean) {
        val serverFragment = supportFragmentManager.findFragmentByTag(ServerSettingsFragment.TAG)
        serverFragment?.let { viewModel.setAppActive((it as ServerSettingsFragment).getServerId(), active) }
        viewModel.setAppActive(ServerManager.SERVER_ID_ACTIVE, active)
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

    fun requestAuthentication(title: String, callback: (AuthenticationResult) -> Boolean): Boolean {
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
