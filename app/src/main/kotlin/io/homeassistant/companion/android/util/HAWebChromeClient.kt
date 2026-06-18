package io.homeassistant.companion.android.util

import android.net.Uri
import android.view.View
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Custom [WebChromeClient] for the Home Assistant frontend WebView.
 *
 * @param onPermissionRequest Callback when the page requests camera/microphone access.
 * @param onJsConfirm Callback when the page calls JavaScript `confirm()`.
 *        Return `true` to indicate the dialog is handled.
 * @param onShowFileChooser Callback when the page requests a file upload.
 *        Return `true` to indicate the file chooser is handled.
 * @param onShowCustomView Callback when the page enters fullscreen (e.g. HTML5 video).
 *        Receives the fullscreen [View] handed over by the WebView.
 * @param onHideCustomView Callback when the page leaves fullscreen.
 */
class HAWebChromeClient(
    private val onPermissionRequest: (PermissionRequest) -> Unit = {},
    private val onJsConfirm: (message: String, result: JsResult) -> Boolean = { _, _ -> false },
    private val onShowFileChooser: (
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ) -> Boolean = { _, _ -> false },
    private val onShowCustomView: (View) -> Unit = {},
    private val onHideCustomView: () -> Unit = {},
) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.let { onPermissionRequest.invoke(it) }
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        if (message != null && result != null) {
            return onJsConfirm.invoke(message, result)
        }
        return super.onJsConfirm(view, url, message, result)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        if (filePathCallback != null && fileChooserParams != null) {
            return onShowFileChooser.invoke(filePathCallback, fileChooserParams)
        }
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        view?.let { onShowCustomView.invoke(it) }
    }

    override fun onHideCustomView() {
        onHideCustomView.invoke()
        super.onHideCustomView()
    }
}
