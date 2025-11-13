package io.homeassistant.companion.android.matter

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.SharedDeviceData
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.matter.views.MatterCommissioningView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MatterCommissioningActivity : AppCompatActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    private val viewModel: MatterCommissioningViewModel by viewModels()
    private var deviceCode: String? = null
    private var deviceName by mutableStateOf<String?>(null)
    private var servers by mutableStateOf<List<Server>>(emptyList())
    private var newMatterDevice = false

    private val threadPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            deviceCode?.let { viewModel.onThreadPermissionResult(result, it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()

        setContent {
            HomeAssistantAppTheme {
                MatterCommissioningView(
                    step = viewModel.step,
                    deviceName = deviceName,
                    servers = servers,
                    onSelectServer = viewModel::checkSupport,
                    onConfirmCommissioning = { startCommissioning() },
                    onClose = { finish() },
                    onContinue = { continueToApp(false) },
                )
            }
        }
        servers = serverManager.defaultServers
    }

    override fun onResume() {
        super.onResume()
        if (intent?.action == Matter.ACTION_COMMISSION_DEVICE) {
            try {
                val data = SharedDeviceData.fromIntent(intent)
                Timber.d(
                    "Matter commissioning data:\n" +
                        "device name: ${data.deviceName}\n" +
                        "room name: ${data.roomName}\n" +
                        "product id: ${data.productId}\n" +
                        "vendor id: ${data.vendorId}\n" +
                        "window expires: ${data.commissioningWindowExpirationMillis}",
                )

                deviceName = data.deviceName
                deviceCode = data.manualPairingCode
                viewModel.checkSetup(newMatterDevice)
                newMatterDevice = false
            } catch (e: SharedDeviceData.InvalidSharedDeviceDataException) {
                Timber.e("Received incomplete Matter commissioning data, launching webview")
                if (!newMatterDevice) continueToApp(true)
            }
        } else {
            Timber.d("No Matter commissioning data, launching webview")
            if (!newMatterDevice) continueToApp(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent // Data is handled by check in onResume()
        newMatterDevice = true
    }

    private fun startCommissioning() {
        lifecycleScope.launch {
            val threadIntent = viewModel.syncThreadIfNecessary()
            if (threadIntent != null) {
                threadPermissionLauncher.launch(IntentSenderRequest.Builder(threadIntent).build())
            } else {
                deviceCode?.let {
                    viewModel.commissionDeviceWithCode(it)
                }
            }
        }
    }

    private fun continueToApp(hideTransition: Boolean) {
        startActivity(WebViewActivity.newInstance(this, null, viewModel.serverId))
        finish()
        if (hideTransition) { // Disable activity start/stop animation
            overridePendingTransition(0, 0)
        }
    }
}
