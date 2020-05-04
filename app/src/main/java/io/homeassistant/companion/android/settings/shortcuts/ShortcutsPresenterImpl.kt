package io.homeassistant.companion.android.settings.shortcuts

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
            integrationUseCase.getPanels()
        }
    }
}
