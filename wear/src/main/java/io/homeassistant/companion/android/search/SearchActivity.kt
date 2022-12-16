package io.homeassistant.companion.android.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.search.views.SearchResultView

@AndroidEntryPoint
class SearchActivity : ComponentActivity() {

    private val searchViewModel by viewModels<SearchViewModel>()
    companion object {
        private const val TAG = "SearchActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, SearchActivity::class.java)
        }
    }

    private var searchResults = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            searchViewModel.searchResult.value = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).let {
                it?.get(0) ?: ""
            }
            searchViewModel.getConversation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val searchIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }
        searchResults.launch(searchIntent)

        setContent {
            SearchResultView(searchViewModel)
        }
    }
}
