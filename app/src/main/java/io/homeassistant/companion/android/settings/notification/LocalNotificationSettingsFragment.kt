package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.settings.notification.views.LocalNotificationSettingsView

// @AndroidEntryPoint
class LocalNotificationSettingsFragment : Fragment() {

    val viewModel: LocalNotificationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    val settings = viewModel.getLocalNotificationSettingFlow(0)
                        .collectAsState(initial = viewModel.getLocalNotificationSetting(0))
                    LocalNotificationSettingsView(
                        localNotificationSetting = settings.value.localNotificationSetting,
                        onSettingChanged = { viewModel.updateLocalNotificationSetting(0, it) }
                    )
                }
            }
        }
    }
}
