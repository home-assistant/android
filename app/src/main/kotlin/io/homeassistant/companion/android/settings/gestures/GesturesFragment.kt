package io.homeassistant.companion.android.settings.gestures

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.gestures.views.GesturesView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import kotlin.getValue

@AndroidEntryPoint
class GesturesFragment : Fragment() {

    val viewModel: GesturesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    GesturesView(
                        gestureActions = viewModel.gestureActions,
                        onSetAction = viewModel::setGestureAction,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.gestures)
    }
}
