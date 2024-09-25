package io.homeassistant.companion.android.improv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.improv.ImprovRepository
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImprovSetupDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var improvRepository: ImprovRepository

    companion object {
        const val TAG = "ImprovSetupDialog"
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
        dialog?.setOnShowListener {
            val dialog = it as BottomSheetDialog
            dialog.window?.setDimAmount(0.03f)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val bottomSheet = dialog.findViewById<View>(R.id.design_bottom_sheet) as FrameLayout
            val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
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
