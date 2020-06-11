package io.homeassistant.companion.android.settings.shortcuts

import android.util.Log
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.Panel
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class ShortcutsPresenterImpl @Inject constructor(
    private val integrationUseCase: IntegrationUseCase
) : ShortcutsPresenter {

    companion object {
        private const val TAG = "ShortcutsPresenter"
    }

    override fun getPanels(): Array<Panel> {
        return runBlocking {
            var panels = arrayOf<Panel>()
            try {
                panels = integrationUseCase.getPanels()
            } catch (e: Exception) {
                Log.e(TAG, "Issue getting panels.", e)
            }
            panels
        }
    }
}
