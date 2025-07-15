package io.homeassistant.companion.android.settings.notification

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import io.homeassistant.companion.android.R

abstract class NotificationMenuProvider : MenuProvider {
    final override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_fragment_notification, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.findItem(R.id.get_help).apply {
            intent =
                Intent(
                    Intent.ACTION_VIEW,
                    "https://companion.home-assistant.io/docs/notifications/notifications-basic".toUri(),
                )
        }
    }
}
