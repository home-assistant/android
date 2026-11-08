package io.homeassistant.companion.android.notifications.callscreen

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.EXTRA_AUTO_ANSWER
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.EXTRA_CALL_ID
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.EXTRA_TAG
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Full screen call UI shown over the lock screen for a call screen notification. It only renders the
 * call owned by [CallScreenManager]; the call keeps running in [CallScreenService] if the UI goes away.
 */
@AndroidEntryPoint
internal class CallScreenActivity : BaseActivity() {

    private val viewModel: CallScreenViewModel by viewModels()

    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) viewModel.onMicrophoneDenied()
            // Answer in any case, without permission the stream is received but not talked to
            viewModel.onAnswer()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        if (!closeIfCallIsStale(intent)) {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (viewModel.isRinging()) viewModel.onSilence() else moveTaskToBack(true)
                    }
                },
            )
            lifecycleScope.launch {
                viewModel.uiState.collect { if (it == null) finish() }
            }
            setContent {
                HATheme {
                    CallScreen(viewModel = viewModel, onAnswer = ::requestAnswer, onAction = ::requestAction)
                }
            }
            handleAutoAnswer(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!closeIfCallIsStale(intent)) handleAutoAnswer(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    override fun onStop() {
        viewModel.onStop()
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            viewModel.isRinging()
        ) {
            viewModel.onSilence()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun configureWindow() {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O_MR1)) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Close when [intent] targets a call that is not the active one, e.g. after the process was killed. */
    private fun closeIfCallIsStale(intent: Intent): Boolean {
        val callId = intent.getLongExtra(EXTRA_CALL_ID, -1)
        if (viewModel.isActive(callId)) return false
        Timber.d("Closing call screen for an inactive call")
        intent.getStringExtra(EXTRA_TAG)?.let { NotificationManagerCompat.from(this).cancel(it, it.hashCode()) }
        finish()
        return true
    }

    private fun handleAutoAnswer(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)) {
            intent.removeExtra(EXTRA_AUTO_ANSWER)
            requestAnswer()
        }
    }

    private fun requestAnswer() {
        if (!viewModel.isRinging()) return
        val permissionMissing = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        if (viewModel.wantsMicrophone() && permissionMissing) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.onAnswer()
        }
    }

    private fun requestAction(action: CallAction) {
        val keyguard = getSystemService<KeyguardManager>()
        if (!action.authenticationRequired || keyguard?.isKeyguardLocked != true) {
            viewModel.onAction(action.index)
        } else if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = viewModel.onAction(action.index)
                },
            )
        } else {
            Timber.w("Not running action ${action.key} that requires authentication on a locked device")
        }
    }
}
