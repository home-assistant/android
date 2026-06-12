package io.homeassistant.companion.android.improv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wifi.improv.ImprovDevice
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.frontend.improv.ImprovRepository
import io.homeassistant.companion.android.frontend.improv.ImprovUIState
import io.homeassistant.companion.android.frontend.improv.ProvisioningEvent
import io.homeassistant.companion.android.frontend.improv.ui.ImprovSheet
import io.homeassistant.companion.android.util.setLayoutAndExpandedByDefault
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage
import io.homeassistant.companion.android.webview.externalbus.ExternalBusRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImprovSetupDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var improvRepository: ImprovRepository

    @Inject
    lateinit var externalBusRepository: ExternalBusRepository

    @Inject
    lateinit var wifiHelper: WifiHelper

    companion object {
        const val TAG = "ImprovSetupDialog"

        private const val ARG_NAME = "name"

        const val RESULT_KEY = "ImprovSetupResult"
        const val RESULT_DOMAIN = "domain"

        fun newInstance(deviceName: String): ImprovSetupDialog {
            return ImprovSetupDialog().apply {
                arguments = bundleOf(ARG_NAME to deviceName)
            }
        }
    }

    private val screenState: MutableStateFlow<ImprovUIState> =
        MutableStateFlow(ImprovUIState.SearchingDevice(deviceName = ""))

    private var provisioningJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val name = arguments?.getString(ARG_NAME).orEmpty()
            screenState.value = ImprovUIState.SearchingDevice(deviceName = name)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val ssid = wifiHelper.getWifiSsid()?.removeSurrounding("\"")
                screenState.update { state ->
                    if (state is ImprovUIState.ConfiguringDevice) state.copy(activeSsid = ssid) else state
                }
                // Subscribing to scanDevices() auto-starts the BLE scan; cancellation on lifecycle
                // exit auto-stops it via the repository's shareIn(WhileSubscribed).
                improvRepository.scanDevices().collect { devices ->
                    screenState.update { state ->
                        if (state is ImprovUIState.SearchingDevice) {
                            val matched = devices.firstOrNull { it.name == state.deviceName }?.address
                            if (matched != null) {
                                ImprovUIState.ConfiguringDevice(
                                    deviceName = state.deviceName,
                                    deviceAddress = matched,
                                    activeSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\""),
                                )
                            } else {
                                state
                            }
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HATheme {
                    val sheetState = rememberHAModalBottomSheetState()
                    HAModalBottomSheet(
                        bottomSheetState = sheetState,
                        onDismissRequest = ::dismiss,
                        dragHandle = {},
                    ) {
                        val state = screenState.collectAsState()
                        ImprovSheet(
                            screenState = state.value,
                            onConnect = { ssid, password -> startProvisioning(ssid, password) },
                            onRestart = { restartConfiguringDevice() },
                            onDismiss = {
                                val domain = (screenState.value as? ImprovUIState.Provisioned)?.domain
                                setFragmentResult(RESULT_KEY, bundleOf(RESULT_DOMAIN to domain))
                                dismiss()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setLayoutAndExpandedByDefault()
    }

    private fun startProvisioning(ssid: String, password: String) {
        val current = screenState.value as? ImprovUIState.ConfiguringDevice ?: return
        val deviceAddress = current.deviceAddress
        val deviceName = current.deviceName
        screenState.value = ImprovUIState.Provisioning(deviceName = deviceName, deviceAddress = deviceAddress)
        provisioningJob?.cancel()
        provisioningJob = lifecycleScope.launch {
            improvRepository.provisionDevice(ImprovDevice(deviceName, deviceAddress), ssid, password).collect { event ->
                when (event) {
                    is ProvisioningEvent.StateChanged -> screenState.update { state ->
                        if (state is ImprovUIState.Provisioning) state.copy(state = event.state) else state
                    }
                    is ProvisioningEvent.ErrorOccurred -> screenState.update { state ->
                        if (state is ImprovUIState.Provisioning) {
                            ImprovUIState.Errored(deviceName, deviceAddress, event.error)
                        } else {
                            state
                        }
                    }
                    is ProvisioningEvent.Provisioned -> {
                        screenState.value = ImprovUIState.Provisioned(domain = event.domain)
                        notifyFrontend()
                    }
                }
            }
        }
    }

    private fun restartConfiguringDevice() {
        provisioningJob?.cancel()
        provisioningJob = null
        val current = screenState.value as? ImprovUIState.WithResolvedDevice ?: return
        screenState.value = ImprovUIState.ConfiguringDevice(
            deviceName = current.deviceName,
            deviceAddress = current.deviceAddress,
            activeSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\""),
        )
    }

    private fun notifyFrontend() {
        lifecycleScope.launch {
            externalBusRepository.send(
                ExternalBusMessage(
                    id = -1,
                    type = "command",
                    command = "improv/device_setup_done",
                ),
            )
        }
    }
}
