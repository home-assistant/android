package io.homeassistant.companion.android.wear.actions

import io.homeassistant.companion.android.common.actions.WearAction

interface ActionsView {
    fun onActionsLoaded(actions: List<WearAction>)
    fun showConfirmation(action: WearAction)
    fun showConfirmed(confirmedType: Int)
}