package io.homeassistant.companion.android.common.data.repositories

import io.homeassistant.companion.android.database.widget.StaticWidgetEntity

interface StaticWidgetRepository : BaseDaoWidgetRepository<StaticWidgetEntity> {

    suspend fun updateWidgetLastUpdate(widgetId: Int, lastUpdate: String)
}
