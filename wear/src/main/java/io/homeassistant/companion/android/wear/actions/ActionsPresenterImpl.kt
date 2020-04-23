package io.homeassistant.companion.android.wear.actions

import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.common.actions.WearActionUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.wear.util.extensions.catch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ActionsPresenterImpl @Inject constructor(
    private val view: ActionsView,
    private val integrationUseCase: IntegrationUseCase,
    private val actionsUseCase: WearActionUseCase
) : ActionsPresenter {

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val actionsFlow = actionsUseCase.getAllActions()

    private val isLoading = AtomicBoolean(false)

    override fun onViewReady() {
        mainScope.launch {
            actionsFlow.collect { actions -> view.onActionsLoaded(actions) }
        }
    }

    override fun onActionClick(action: WearAction) {
        if (isLoading.compareAndSet(false, true)) {
            view.showConfirmation(action)
        }
    }

    override fun executeAction(action: WearAction) {
        mainScope.launch {
            val actionMap = mapOf("action" to action.action)
            val result = withContext(Dispatchers.IO) {
                catch { integrationUseCase.fireEvent("mobile_app_wear_action", actionMap) }
            }
            val confirmType = if (result != null) ConfirmationActivity.SUCCESS_ANIMATION
                                   else ConfirmationActivity.FAILURE_ANIMATION
            isLoading.set(false)
            view.showConfirmed(confirmType)
        }
    }

    override fun finish() {
        mainScope.cancel()
    }
}