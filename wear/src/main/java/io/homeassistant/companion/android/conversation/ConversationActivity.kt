package io.homeassistant.companion.android.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.conversation.views.SearchResultView
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

    private var searchResults = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            conversationViewModel.speechResult.value = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).let {
                it?.get(0) ?: ""
            }
            conversationViewModel.getConversation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            conversationViewModel.isSupportConversation()
            if (conversationViewModel.supportsConversation.value) {
                val searchIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                }
                searchResults.launch(searchIntent)
            }
        }

        setContent {
            SearchResultView(conversationViewModel)
        }
    }
}
