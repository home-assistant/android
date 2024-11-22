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
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import io.homeassistant.companion.android.improv.ImprovRepository
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.setLayoutAndExpandedByDefault
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
    lateinit var wifiHelper: WifiHelper

    companion object {
        const val TAG = "ImprovSetupDialog"

        const val RESULT_KEY = "ImprovSetupResult"
        const val RESULT_DOMAIN = "domain"
    }

    private val screenState = MutableStateFlow(
        ImprovSheetState(
            scanning = false,
            devices = listOf(),
            deviceState = null,
            errorState = null
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
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
                    }
                }
                launch {
                    improvRepository.getDeviceState().collect {
                        screenState.emit(screenState.value.copy(deviceState = it))
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
                            val domain = improvRepository.getResultState().firstOrNull()?.toHttpUrlOrNull()?.queryParameter("domain")
                            setFragmentResult(RESULT_KEY, bundleOf(RESULT_DOMAIN to domain))
                            improvRepository.clearStatesForDevice()
                            dismiss()
                        }
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
}
