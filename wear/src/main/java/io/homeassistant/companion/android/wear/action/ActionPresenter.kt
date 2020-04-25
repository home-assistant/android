package io.homeassistant.companion.android.wear.action

import io.homeassistant.companion.android.common.actions.WearAction

interface ActionPresenter {

    fun onViewReady()
    fun saveAction(action: WearAction)
    fun finish()

}