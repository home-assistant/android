package io.homeassistant.companion.android.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import io.homeassistant.companion.android.onboarding.welcome.WelcomeFragment
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@AndroidEntryPoint
class OnboardingActivity : BaseActivity() {

    companion object {
        private const val AUTHENTICATION_FRAGMENT = "authentication_fragment"
        const val AUTH_CALLBACK = "homeassistant://auth-callback"
        private const val TAG = "OnboardingActivity"

        fun buildAuthUrl(context: Context, base: String): String {
            return try {
                val url = base.toHttpUrl()
                val builder = if (url.host.endsWith("ui.nabu.casa", true)) {
                    HttpUrl.Builder()
                        .scheme(url.scheme)
                        .host(url.host)
                        .port(url.port)
                } else {
                    url.newBuilder()
                }
                builder
                    .addPathSegments("auth/authorize")
                    .addEncodedQueryParameter("response_type", "code")
                    .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
                    .addEncodedQueryParameter("redirect_uri", AUTH_CALLBACK)
                    .build()
                    .toString()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to build authentication URL", e)
                Toast.makeText(context, io.homeassistant.companion.android.common.R.string.error_connection_failed, Toast.LENGTH_LONG).show()
                ""
            }
        }
    }

    private val viewModel by viewModels<OnboardingViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val input = OnboardApp.parseInput(intent)
        viewModel.deviceName.value = input.defaultDeviceName
        viewModel.locationTrackingPossible.value = input.locationTrackingPossible
        viewModel.notificationsPossible.value = input.notificationsPossible
        viewModel.notificationsEnabled = if (input.notificationsPossible) {
            BuildConfig.FLAVOR == "full" &&
                (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        NotificationManagerCompat.from(this).areNotificationsEnabled()
                    )
        } else {
            false
        }
        viewModel.deviceIsWatch = input.isWatch
        viewModel.discoveryOptions = input.discoveryOptions
        viewModel.mayRequireTlsClientCertificate = input.mayRequireTlsClientCertificate

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.content, WelcomeFragment::class.java, null)
            }
            if (input.url != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    supportFragmentManager.commit {
                        replace(R.id.content, DiscoveryFragment::class.java, null)
                        addToBackStack(null)
                    }
                }
                if (input.url.isNotBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    viewModel.onManualUrlUpdated(input.url)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !viewModel.manualContinueEnabled) {
                        supportFragmentManager.commit {
                            replace(R.id.content, ManualSetupFragment::class.java, null)
                            addToBackStack(null)
                        }
                    }
                    if (viewModel.manualContinueEnabled) {
                        val uri = buildAuthUrl(baseContext, input.url)
                        val builder = CustomTabsIntent.Builder()
                        val customTabsIntent = builder.build()
                        customTabsIntent.launchUrl(baseContext, Uri.parse(uri))
                    }
                }
            }
        }

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data
            if (uri != null && uri.scheme == "homeassistant") {
                handleAuthCallback(uri.toString())
            }
        }

        val onBackPressed = object : OnBackPressedCallback(supportFragmentManager.backStackEntryCount > 0) {
            override fun handleOnBackPressed() {
                supportFragmentManager.popBackStack()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressed)
        supportFragmentManager.addOnBackStackChangedListener {
            onBackPressed.isEnabled = supportFragmentManager.backStackEntryCount > 0
        }
    }

    private fun handleAuthCallback(url: String) {
        val code = Uri.parse(url).getQueryParameter("code")
        if (url.startsWith(AUTH_CALLBACK) && !code.isNullOrBlank()) {
            viewModel.registerAuthCode(code)
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.content, MobileAppIntegrationFragment::class.java, null)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Workaround to sideload on Android TV and use a remote for basic navigation in WebView
        val fragmentManager = supportFragmentManager.findFragmentByTag(AUTHENTICATION_FRAGMENT)
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN &&
            fragmentManager != null && fragmentManager.isVisible
        ) {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            return true
        }

        return super.dispatchKeyEvent(event)
    }
}
