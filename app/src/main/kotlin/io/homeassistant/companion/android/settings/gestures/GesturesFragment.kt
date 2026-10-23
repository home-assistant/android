package io.homeassistant.companion.android.settings.gestures

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.gestures.views.GesturesScreen
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import kotlin.getValue

@AndroidEntryPoint
class GesturesFragment : Fragment() {

    val viewModel: GesturesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                val systemGestures = WindowInsets.systemGestures
                val tappableElements = WindowInsets.tappableElement
                // Button navigation can occupy a side in landscape without reserving its edge for Back.
                val leftEdgeReserved = systemGestures.getLeft(density, layoutDirection) >
                    tappableElements.getLeft(density, layoutDirection)
                val rightEdgeReserved = systemGestures.getRight(density, layoutDirection) >
                    tappableElements.getRight(density, layoutDirection)
                LaunchedEffect(leftEdgeReserved, rightEdgeReserved) {
                    viewModel.updateGestureAvailability(
                        leftEdgeReserved = leftEdgeReserved,
                        rightEdgeReserved = rightEdgeReserved,
                    )
                }
                HomeAssistantAppTheme {
                    GesturesScreen(
                        gestureActions = viewModel.gestureActions,
                        availableGestures = viewModel.availableGestures,
                        onSetAction = viewModel::setGestureAction,
                        onToolbarTitleChanged = { title ->
                            activity?.title = title
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/integrations/gestures")
    }

    override fun onResume() {
        super.onResume()
        ViewCompat.requestApplyInsets(requireView())
    }
}
