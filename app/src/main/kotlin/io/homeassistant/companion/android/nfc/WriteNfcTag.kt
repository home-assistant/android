package io.homeassistant.companion.android.nfc

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class WriteNfcTag : ActivityResultContract<WriteNfcTag.Input, Int>() {

    data class Input(val tagId: String? = null, val messageId: Int = -1)

    override fun createIntent(context: Context, input: Input): Intent {
        return NfcSetupActivity.newInstance(context, input.tagId, input.messageId)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Int {
        return resultCode
    }
}
