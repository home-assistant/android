package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContract

class ShowWebFileChooser : ActivityResultContract<WebChromeClient.FileChooserParams, Array<Uri>?>() {

    override fun createIntent(context: Context, input: WebChromeClient.FileChooserParams): Intent {
        return input.createIntent().apply {
            type = "*/*"
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Array<Uri>? {
        return WebChromeClient.FileChooserParams.parseResult(resultCode, intent)
    }
}
