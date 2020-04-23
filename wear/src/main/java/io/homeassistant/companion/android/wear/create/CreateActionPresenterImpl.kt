package io.homeassistant.companion.android.wear.create

import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.common.actions.WearActionUseCase
import io.homeassistant.companion.android.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CreateActionPresenterImpl @Inject constructor(
    private val view: CreateActionView,
    private val wearActionUseCase: WearActionUseCase
) : CreateActionPresenter {

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() = Unit

    override fun createAction(action: WearAction) {
        mainScope.launch {
            val success = withContext(Dispatchers.IO) { wearActionUseCase.createAction(action) }
            if (success) {
                view.showConfirmed(ConfirmationActivity.SUCCESS_ANIMATION, R.string.confirmation_action_created)
            } else {
                view.showConfirmed(ConfirmationActivity.FAILURE_ANIMATION, R.string.confirmation_action_created_failure)
            }
        }
    }

    override fun finish() {
        mainScope.cancel()
    }

}