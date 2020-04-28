package io.homeassistant.companion.android.shortcuts

import io.homeassistant.companion.android.domain.integration.Panel

interface ShortcutsPresenter {
    fun onCreate()

    fun onFinish()

    fun getPanels(): Array<Panel>
}
