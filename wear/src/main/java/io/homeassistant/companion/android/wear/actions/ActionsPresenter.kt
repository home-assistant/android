package io.homeassistant.companion.android.wear.actions

import io.homeassistant.companion.android.common.actions.WearAction

interface ActionsPresenter {
    fun onViewReady()
    fun onActionClick(action: WearAction)
    fun executeAction(action: WearAction)
    fun finish()
}