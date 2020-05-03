package io.homeassistant.companion.android.settings.shortcuts

import io.homeassistant.companion.android.domain.integration.Panel

interface ShortcutsPresenter {
    fun getPanels(): Array<Panel>
}
