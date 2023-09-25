package io.homeassistant.companion.android.settings.vehicle

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.settings.addHelpMenuProvider
import io.homeassistant.companion.android.settings.vehicle.views.AndroidAutoFavoritesSettings
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ManageAndroidAutoSettingsFragment : Fragment() {

    @Inject
    lateinit var serverManager: ServerManager

    val viewModel: ManageAndroidAutoViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    AndroidAutoFavoritesSettings(
                        androidAutoViewModel = viewModel,
                        serversList = serverManager.defaultServers,
                        defaultServer = serverManager.getServer()?.id ?: 0
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addHelpMenuProvider("https://companion.home-assistant.io/docs/android-auto")
    }

    override fun onResume() {
        super.onResume()
        activity?.title =
            if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                getString(commonR.string.android_automotive_favorites)
            } else {
                getString(commonR.string.aa_favorites)
            }
    }
}
