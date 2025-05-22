package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.notifications.PushProvider
import javax.inject.Inject

class PushManagerImpl @Inject constructor() : PushManagerBase(emptyMap()) {
    override val defaultProvider: PushProvider?
        get() = null
}