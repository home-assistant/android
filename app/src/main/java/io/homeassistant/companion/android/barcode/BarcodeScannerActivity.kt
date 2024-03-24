package io.homeassistant.companion.android.barcode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.barcode.view.BarcodeScannerView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class BarcodeScannerActivity : BaseActivity() {

    companion object {
        private const val TAG = "BarcodeScannerActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, BarcodeScannerActivity::class.java).apply {
                // TODO extras?
            }
        }
    }

    private val viewModel: BarcodeScannerViewModel by viewModels()

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.checkPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            HomeAssistantAppTheme {
                BarcodeScannerView(
                    title = "Scan QR code",
                    subtitle = "Find the code on your device",
                    action = "Enter code manually",
                    hasPermission = viewModel.hasPermission,
                    requestPermission = {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    onResult = {
                        // TODO return to WebViewActivity
                        Log.d(TAG, "Decoded $it")
                        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                    },
                    onCancel = { forAction ->
                        finish()
                    }
                )
            }
        }
    }
}
