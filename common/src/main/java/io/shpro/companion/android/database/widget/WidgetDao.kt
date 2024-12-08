package io.shpro.companion.android.database.widget

interface WidgetDao {
    suspend fun delete(id: Int)
}
