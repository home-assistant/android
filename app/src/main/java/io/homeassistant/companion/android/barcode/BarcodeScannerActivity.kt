package io.homeassistant.companion.android.barcode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.barcode.view.BarcodeScannerView
import io.homeassistant.companion.android.barcode.view.barcodeScannerOverlayColor
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class BarcodeScannerActivity : BaseActivity() {

    companion object {
        private const val TAG = "BarcodeScannerActivity"

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_ACTION = "action"

        fun newInstance(
            context: Context,
            title: String,
            subtitle: String,
            action: String?
        ): Intent {
            return Intent(context, BarcodeScannerActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_ACTION, action)
            }
        }
    }

    private val viewModel: BarcodeScannerViewModel by viewModels()

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.checkPermission()
        requestSilently = false
    }

    private var requestSilently by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val overlaySystemBarStyle = SystemBarStyle.dark(barcodeScannerOverlayColor.toArgb())
        enableEdgeToEdge(overlaySystemBarStyle, overlaySystemBarStyle)
        super.onCreate(savedInstanceState)

        val title = if (intent.hasExtra(EXTRA_TITLE)) intent.getStringExtra(EXTRA_TITLE) else null
        val subtitle = if (intent.hasExtra(EXTRA_SUBTITLE)) intent.getStringExtra(EXTRA_SUBTITLE) else null
        if (title == null || subtitle == null) finish() // Invalid state
        val action = if (intent.hasExtra(EXTRA_ACTION)) intent.getStringExtra(EXTRA_ACTION) else null

        setContent {
            HomeAssistantAppTheme {
                BarcodeScannerView(
                    title = title!!,
                    subtitle = subtitle!!,
                    action = action,
                    hasFlashlight = viewModel.hasFlashlight,
                    hasPermission = viewModel.hasPermission,
                    requestPermission = { requestPermission(false) },
                    didRequestPermission = !requestSilently,
                    onResult = {
                        // TODO return to WebViewActivity / external bus
                        Log.d(TAG, "Decoded $it")
                        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                    },
                    onCancel = { forAction ->
                        // TODO return to WebViewActivity / external bus
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermission()
        if (!viewModel.hasPermission && requestSilently) {
            requestPermission(true)
        }
    }

    private fun requestPermission(inContext: Boolean) {
        if (inContext) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            requestSilently = true // Reset state to trigger new in context dialog/check when resumed
        }
    }
}
