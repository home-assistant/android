package io.homeassistant.companion.android.matter

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.SharedDeviceData
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.matter.views.MatterCommissioningView
import io.homeassistant.companion.android.webview.WebViewActivity

@AndroidEntryPoint
class MatterCommissioningActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MatterCommissioningActi"
    }

    private val viewModel: MatterCommissioningViewModel by viewModels()
    private var deviceCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                MatterCommissioningView(
                    step = viewModel.step,
                    onConfirmCommissioning = { deviceCode?.let { viewModel.commissionDeviceWithCode(it) } },
                    onClose = { finish() },
                    onContinue = { continueToApp(false) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent?.action == Matter.ACTION_COMMISSION_DEVICE) {
            try {
                val data = SharedDeviceData.fromIntent(intent)
                Log.d(
                    TAG,
                    "Matter commissioning data:\n" +
                        "device name: ${data.deviceName}\n" +
                        "room name: ${data.roomName}\n" +
                        "device type: ${data.deviceType}\n" +
                        "product id: ${data.productId}\n" +
                        "vendor id: ${data.vendorId}\n" +
                        "window expires: ${data.commissioningWindowExpirationMillis}"
                )

                deviceCode = data.manualPairingCode
                viewModel.checkSupport()
            } catch (e: SharedDeviceData.InvalidSharedDeviceDataException) {
                Log.e(TAG, "Received incomplete Matter commissioning data, launching webview")
                continueToApp(true)
            }
        } else {
            Log.d(TAG, "No Matter commissioning data, launching webview")
            continueToApp(true)
        }
    }

    private fun continueToApp(hideTransition: Boolean) {
        startActivity(WebViewActivity.newInstance(this))
        finish()
        if (hideTransition) { // Disable activity start/stop animation
            overridePendingTransition(0, 0)
        }
    }
}
