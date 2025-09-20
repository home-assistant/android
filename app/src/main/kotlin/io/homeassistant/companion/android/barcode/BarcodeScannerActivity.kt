package io.homeassistant.companion.android.barcode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.barcode.view.BarcodeScannerView
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BarcodeScannerActivity : BaseActivity() {

    companion object {
        private const val EXTRA_MESSAGE_ID = "message_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_ACTION = "action"

        fun newInstance(context: Context, messageId: Int, title: String, subtitle: String, action: String?): Intent {
            return Intent(context, BarcodeScannerActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE_ID, messageId)
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
        super.onCreate(savedInstanceState)

        val messageId = intent.getIntExtra(EXTRA_MESSAGE_ID, -1)

        val title = if (intent.hasExtra(EXTRA_TITLE)) intent.getStringExtra(EXTRA_TITLE) else null
        val subtitle = if (intent.hasExtra(EXTRA_SUBTITLE)) intent.getStringExtra(EXTRA_SUBTITLE) else null
        if (title == null || subtitle == null) finish() // Invalid state
        val action = if (intent.hasExtra(EXTRA_ACTION)) intent.getStringExtra(EXTRA_ACTION) else null

        // Enforce status bar to be always light so we can see it above the blur of the screen
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

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
                    onResult = { text, format ->
                        val frontendFormat = when (format) {
                            BarcodeFormat.PDF_417 -> "pdf417"
                            BarcodeFormat.MAXICODE,
                            BarcodeFormat.RSS_14,
                            BarcodeFormat.RSS_EXPANDED,
                            BarcodeFormat.UPC_EAN_EXTENSION,
                            -> "unknown"
                            else -> format.toString().lowercase(Locale.getDefault())
                        }
                        viewModel.sendScannerResult(messageId, text, frontendFormat)
                    },
                    onCancel = { forAction ->
                        viewModel.sendScannerClosing(messageId, forAction)
                        finish()
                    },
                )
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            viewModel.sendScannerClosing(messageId, false)
            finish()
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.actionsFlow.collect {
                    when (it.type) {
                        BarcodeActionType.NOTIFY -> {
                            if (it.message.isNullOrBlank()) return@collect
                            AlertDialog.Builder(this@BarcodeScannerActivity)
                                .setMessage(it.message)
                                .setPositiveButton(commonR.string.ok, null)
                                .show()
                        }
                        BarcodeActionType.CLOSE -> finish()
                    }
                }
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
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
            requestSilently = true // Reset state to trigger new in context dialog/check when resumed
        }
    }
}
