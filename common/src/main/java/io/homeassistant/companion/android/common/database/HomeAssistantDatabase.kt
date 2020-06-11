package io.homeassistant.companion.android.common.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.homeassistant.companion.android.common.actions.WearAction

@Database(
    version = 1,
    entities = [
        WearAction::class
    ],
    exportSchema = true
)
abstract class HomeAssistantDatabase : RoomDatabase() {

    abstract fun wearActionsDao(): WearActionsDao
}
