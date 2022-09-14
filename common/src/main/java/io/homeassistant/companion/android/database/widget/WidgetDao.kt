package io.homeassistant.companion.android.database.widget

interface WidgetDao {
    suspend fun delete(id: Int)
}
