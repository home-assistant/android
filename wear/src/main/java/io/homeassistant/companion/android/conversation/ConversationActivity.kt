package io.homeassistant.companion.android.conversation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.util.AudioUrlPlayerService
import io.homeassistant.companion.android.conversation.views.LoadAssistView
import io.homeassistant.companion.android.home.HomeActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationActivity : ComponentActivity() {

    private val conversationViewModel by viewModels<ConversationViewModel>()
    companion object {
        private const val TAG = "ConvActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, ConversationActivity::class.java)
        }
    }

    private val searchResults = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            conversationViewModel.updateSpeechResult(
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).let {
                    it?.get(0) ?: ""
                }
            )
        }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { conversationViewModel.onPermissionResult(it, this::launchVoiceInputIntent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val playerIntent = Intent(this, AudioUrlPlayerService::class.java)
        startService(playerIntent)
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        lifecycleScope.launch {
            val launchIntent = conversationViewModel.onCreate(hasRecordingPermission())
            if (launchIntent) {
                launchVoiceInputIntent()
            } else if (!conversationViewModel.isRegistered()) {
                startActivity(HomeActivity.newInstance(this@ConversationActivity))
                finish()
            }
        }

        setContent {
            LoadAssistView(
                conversationViewModel = conversationViewModel,
                onVoiceInputIntent = this::launchVoiceInputIntent
            )
        }
    }

    override fun onResume() {
        super.onResume()
        conversationViewModel.setPermissionInfo(hasRecordingPermission()) { requestPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    override fun onPause() {
        super.onPause()
        conversationViewModel.onPause()
        val pm = applicationContext.getSystemService<PowerManager>()
        if (pm?.isInteractive == false && conversationViewModel.conversation.size >= 3) {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        this.intent = intent

        val launchIntent = conversationViewModel.onNewIntent(intent)
        if (launchIntent) {
            launchVoiceInputIntent()
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        private var player: Messenger? = null
        private var serviceBound = false

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            player = Messenger(service)
            serviceBound = true
            conversationViewModel.setAudioPlayer(this::startAudio, this::stopAudio)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            player = null
            serviceBound = false
            conversationViewModel.clearAudioPlayer()
        }

        fun startAudio(path: String) {
            if (serviceBound) {
                player?.send(Message.obtain(null, AudioUrlPlayerService.MSG_START_PLAYBACK, AudioUrlPlayerService.PlaybackRequestMessage(path, true)))
            }
        }

        fun stopAudio() {
            if (serviceBound) {
                player?.send(Message.obtain(null, AudioUrlPlayerService.MSG_STOP_PLAYBACK, null))
            }
        }
    }

    private fun hasRecordingPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun launchVoiceInputIntent() {
        val searchIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }
        searchResults.launch(searchIntent)
    }
}
