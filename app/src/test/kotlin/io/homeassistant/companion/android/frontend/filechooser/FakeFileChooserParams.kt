package io.homeassistant.companion.android.frontend.filechooser

import android.content.Intent
import android.webkit.WebChromeClient.FileChooserParams

internal class FakeFileChooserParams(
    private val acceptTypes: Array<String> = emptyArray(),
    private val mode: Int = MODE_OPEN,
    private val captureEnabled: Boolean = false,
    private val filenameHint: String? = null,
    private val permissionMode: Int = PERMISSION_MODE_READ,
) : FileChooserParams() {
    override fun getMode(): Int = mode
    override fun getAcceptTypes(): Array<String> = acceptTypes
    override fun isCaptureEnabled(): Boolean = captureEnabled
    override fun getTitle(): CharSequence? = null
    override fun getFilenameHint(): String? = filenameHint
    override fun getPermissionMode(): Int = permissionMode
    override fun createIntent(): Intent = error("Not used by ShowWebFileChooser")
}
