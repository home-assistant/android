package io.homeassistant.companion.android.wear.create

import io.homeassistant.companion.android.common.actions.WearAction

interface CreateActionPresenter {

    fun onViewReady()
    fun createAction(action: WearAction)
    fun finish()

}