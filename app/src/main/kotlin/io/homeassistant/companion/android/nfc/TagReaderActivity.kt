package io.homeassistant.companion.android.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.nfc.views.TagReaderScreen

@AndroidEntryPoint
class TagReaderActivity : BaseActivity() {

    private val viewModel: TagReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isNfcTag = intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED
        val isQrTag = intent.action == Intent.ACTION_VIEW

        setContent {
            HATheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                TagReaderScreen(
                    state = state,
                    onAllowOnce = viewModel::onAllowOnce,
                    onAllowAlways = viewModel::onAllowAlways,
                    onDismissed = viewModel::onDismissed,
                    onErrorAcknowledged = viewModel::onErrorAcknowledged,
                    onFinished = ::finish,
                )
            }
        }

        if (isNfcTag || isQrTag) {
            val url = if (isNfcTag) NFCUtil.extractUrlFromNFCIntent(intent) else intent.data
            viewModel.onIntentReceived(url = url, isNfcTag = isNfcTag)
        } else {
            finish()
        }
    }
}
