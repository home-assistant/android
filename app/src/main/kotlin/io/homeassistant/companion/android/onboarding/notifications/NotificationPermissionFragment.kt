package io.homeassistant.companion.android.onboarding.notifications

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import timber.log.Timber

class NotificationPermissionFragment : Fragment() {

    private val viewModel by activityViewModels<OnboardingViewModel>()

    private val permissionsRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Timber.i("Notification permission was ${if (isGranted) "granted" else "not granted"}")
            viewModel.setNotifications(isGranted)

            if (isGranted) {
                onComplete()
            } else {
                showPermissionDeniedDialog()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    NotificationPermissionView(
                        onSetNotificationsEnabled = ::setNotifications,
                    )
                }
            }
        }
    }

    private fun setNotifications(enabled: Boolean) {
        if (enabled) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            ) {
                permissionsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setNotifications(true)
                onComplete()
            }
        } else {
            viewModel.setNotifications(false)
            onComplete()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage(commonR.string.onboarding_notifications_denied)
            .setPositiveButton(commonR.string.continue_connect) { _, _ ->
                onComplete()
            }
            .setCancelable(false)
            .show()
    }

    private fun onComplete() {
        activity?.setResult(Activity.RESULT_OK, viewModel.getOutput().toIntent())
        activity?.finish()
    }
}
