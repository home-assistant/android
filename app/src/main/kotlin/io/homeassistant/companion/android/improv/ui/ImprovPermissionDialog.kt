package io.homeassistant.companion.android.improv.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.frontend.improv.ImprovRepository
import io.homeassistant.companion.android.frontend.improv.ui.ImprovPermission
import io.homeassistant.companion.android.util.setLayoutAndExpandedByDefault
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImprovPermissionDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var improvRepository: ImprovRepository

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            setFragmentResult(RESULT_KEY, bundleOf(RESULT_GRANTED to it.all { result -> result.value }))
            dismiss()
        }

    companion object {
        const val TAG = "ImprovPermissionDialog"

        const val RESULT_KEY = "ImprovPermissionResult"
        const val RESULT_GRANTED = "granted"
    }

    private var neededPermissions = arrayOf<String>()
    private var increasedCount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = improvRepository.requiredPermissions
        context?.let { ctx ->
            permissions.forEach {
                val granted = ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
                if (!granted) neededPermissions += it
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
                        ImprovPermission(
                            needsBluetooth = neededPermissions.any { it.contains("BLUETOOTH", ignoreCase = true) },
                            needsLocation = neededPermissions.any { it == Manifest.permission.ACCESS_FINE_LOCATION },
                            onContinue = { requestPermissions.launch(neededPermissions) },
                            onSkip = ::dismiss,
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

    override fun onResume() {
        super.onResume()
        if (increasedCount) return
        lifecycleScope.launch {
            prefsRepository.addImprovPermissionDisplayedCount()
            increasedCount = true
        }
    }
}
