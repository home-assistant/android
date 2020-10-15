package io.homeassistant.companion.android.settings.notification

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class NotificationDetailFragment(
    private val notification: NotificationItem
) :
    PreferenceFragmentCompat() {

    companion object {
        fun newInstance(
            notification: NotificationItem
        ): NotificationDetailFragment {
            // No op
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // No op
    }
}
