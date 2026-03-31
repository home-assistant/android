package io.homeassistant.companion.android.improv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wifi.improv.DeviceState
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.improv.ImprovRepository
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.setLayoutAndExpandedByDefault
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage
import io.homeassistant.companion.android.webview.externalbus.ExternalBusRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

        fun newInstance(deviceName: String?): ImprovSetupDialog {
            return ImprovSetupDialog().apply {
                arguments = bundleOf(ARG_NAME to deviceName)
            }
        }
    }

    private val screenState = MutableStateFlow(
        ImprovSheetState(
            scanning = false,
            devices = listOf(),
            deviceState = null,
            errorState = null,
        ),
    )

    private var initialDeviceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (savedInstanceState == null) {
                if (arguments?.containsKey(ARG_NAME) == true) {
                    val name = arguments?.getString(ARG_NAME, "").takeIf { !it.isNullOrBlank() }
                    name?.let {
                        initialDeviceName = it
                        screenState.emit(screenState.value.copy(initialDeviceName = it))
                    }
                }
            }
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                screenState.emit(screenState.value.copy(activeSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\"")))
                launch {
                    improvRepository.getScanningState().collect {
                        screenState.emit(screenState.value.copy(scanning = it))
                    }
                }
                launch {
                    improvRepository.getDevices().collect {
                        screenState.emit(screenState.value.copy(devices = it))
                        if (initialDeviceName != null) {
                            it.firstOrNull { device -> device.name == initialDeviceName }
                                ?.let { foundDevice ->
                                    screenState.emit(
                                        screenState.value.copy(
                                            initialDeviceAddress = foundDevice.address,
                                        ),
                                    )
                                    initialDeviceName = null
                                }
                        }
                    }
                }
                launch {
                    improvRepository.getDeviceState().collect {
                        screenState.emit(screenState.value.copy(deviceState = it))
                        if (it == DeviceState.PROVISIONED) notifyFrontend()
                    }
                }
                launch {
                    improvRepository.getErrorState().collect {
                        screenState.emit(screenState.value.copy(errorState = it))
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    val state = screenState.collectAsState()
                    ImprovSheetView(
                        screenState = state.value,
                        onConnect = improvRepository::connectAndSubmit,
                        onRestart = {
                            improvRepository.clearStatesForDevice()
                            startScanning()
                        },
                        onDismiss = {
                            val domain = improvRepository.getResultState().firstOrNull()?.toHttpUrlOrNull()
                                ?.queryParameter("domain")
                            setFragmentResult(RESULT_KEY, bundleOf(RESULT_DOMAIN to domain))
                            improvRepository.clearStatesForDevice()
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setLayoutAndExpandedByDefault()
    }

    override fun onResume() {
        super.onResume()
        if (screenState.value.deviceState == null) startScanning()
    }

    override fun onPause() {
        improvRepository.stopScanning()
        super.onPause()
    }

    private fun startScanning() {
        context?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                improvRepository.startScanning(it)
            }
        }
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
