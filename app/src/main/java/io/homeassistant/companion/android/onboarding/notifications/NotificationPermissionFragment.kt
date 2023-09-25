package io.homeassistant.companion.android.onboarding.notifications

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.common.R as commonR

class NotificationPermissionFragment : Fragment() {

    companion object {
        private const val TAG = "NotificationPermFrag"
    }

    private val viewModel by activityViewModels<OnboardingViewModel>()

    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Log.i(TAG, "Notification permission was ${if (isGranted) "granted" else "not granted"}")
        viewModel.setNotifications(isGranted)

        if (isGranted) {
            onComplete()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    NotificationPermissionView(
                        onSetNotificationsEnabled = ::setNotifications
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
