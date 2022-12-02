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
    private var devicePin: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                MatterCommissioningView(
                    step = viewModel.step,
                    onConfirmCommissioning = { devicePin?.let { viewModel.commissionDeviceWithPin(it) } },
                    onClose = { finish() },
                    onContinue = {
                        startActivity(WebViewActivity.newInstance(this))
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent?.action == Matter.ACTION_COMMISSION_DEVICE) {
            // TODO reduce log data (especially pairing code)
            val data = SharedDeviceData.fromIntent(intent)
            Log.d(
                TAG,
                "Matter commissioning data:\n" +
                    "device name: ${data.deviceName}\n" +
                    "device type: ${data.deviceType}\n" +
                    "window expires: ${data.commissioningWindowExpirationMillis}\n" +
                    "product id: ${data.productId}\n" +
                    "manual pairing code: ${data.manualPairingCode}\n" +
                    "room name: ${data.roomName}\n" +
                    "vendor id: ${data.vendorId}"
            )

            devicePin = data.manualPairingCode.toLongOrNull()
            viewModel.checkSupport()
        } else {
            Log.d(TAG, "No Matter commissioning data, launching webview")

            startActivity(WebViewActivity.newInstance(this))
            finish()
        }
    }
}
