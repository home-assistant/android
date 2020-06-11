package io.homeassistant.companion.android.wear.navigation

import androidx.annotation.StringRes

interface NavigationView {
    fun displayError(@StringRes messageId: Int)
}
