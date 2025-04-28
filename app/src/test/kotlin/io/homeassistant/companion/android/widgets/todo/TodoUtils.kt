package io.homeassistant.companion.android.widgets.todo

import io.homeassistant.companion.android.common.data.integration.Entity
import java.util.Calendar

internal fun fakeServerEntity(entityId: String, friendlyName: String? = null): Entity<Map<String, Any>> {
    return Entity(
        entityId = entityId,
        state = "",
        attributes = if (friendlyName != null) mapOf("friendly_name" to friendlyName) else emptyMap(),
        lastChanged = Calendar.getInstance(),
        lastUpdated = Calendar.getInstance(),
        context = null,
    ) as Entity<Map<String, Any>>
}
