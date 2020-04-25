package io.homeassistant.companion.android.wear.action

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

class ActionPresenterImpl @Inject constructor(
    private val view: ActionView,
    private val wearActionUseCase: WearActionUseCase
) : ActionPresenter {

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() = Unit

    override fun saveAction(action: WearAction) {
        mainScope.launch {
            val success = withContext(Dispatchers.IO) { wearActionUseCase.saveAction(action) }
            if (success) {
                val message =
                    if (action.id != null) R.string.confirmation_action_updated
                    else R.string.confirmation_action_created
                view.showConfirmed(ConfirmationActivity.SUCCESS_ANIMATION, message)
            } else {
                val message =
                    if (action.id != null) R.string.confirmation_action_updated_failure
                    else R.string.confirmation_action_created_failure
                view.showConfirmed(ConfirmationActivity.FAILURE_ANIMATION, message)
            }
        }
    }

    override fun finish() {
        mainScope.cancel()
    }

}