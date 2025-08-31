package io.homeassistant.companion.android.conversation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import io.homeassistant.companion.android.conversation.views.LoadAssistView
import io.homeassistant.companion.android.home.HomeActivity
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationActivity : ComponentActivity() {

    private val conversationViewModel by viewModels<ConversationViewModel>()
    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, ConversationActivity::class.java)
        }
    }

    private val searchResults = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            conversationViewModel.updateSpeechResult(
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).let {
                    it?.get(0) ?: ""
                },
            )
        }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { conversationViewModel.onPermissionResult(it, this::launchVoiceInputIntent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

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
                onVoiceInputIntent = this::launchVoiceInputIntent,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        conversationViewModel.setPermissionInfo(hasRecordingPermission()) {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onPause() {
        super.onPause()
        conversationViewModel.onPause()
        val pm = applicationContext.getSystemService<PowerManager>()
        if (pm?.isInteractive == false && conversationViewModel.conversation.size >= 3) {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent

        val launchIntent = conversationViewModel.onNewIntent(intent)
        if (launchIntent) {
            launchVoiceInputIntent()
        }
    }

    private fun hasRecordingPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun launchVoiceInputIntent() {
        val searchIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
        }
        searchResults.launch(searchIntent)
    }
}
