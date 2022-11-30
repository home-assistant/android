package io.homeassistant.companion.android.matter

import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.SharedDeviceData
import io.homeassistant.companion.android.webview.WebViewActivity

class MatterCommissioningActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MatterCommissioningActi"
    }

    override fun onResume() {
        super.onResume()

        if (intent?.action == Matter.ACTION_COMMISSION_DEVICE) {
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
            getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("", data.manualPairingCode))
        } else {
            Log.d(TAG, "NO MATTER COMMISSIONING DATA")
        }

        startActivity(WebViewActivity.newInstance(this))
        finish()
    }
}
