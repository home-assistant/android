package io.homeassistant.companion.android.common.database

import android.content.Context
import android.os.Debug
import androidx.room.Room
import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.common.dagger.DataComponent
import io.homeassistant.companion.android.common.dagger.DataScope
import io.homeassistant.companion.android.common.dagger.DomainComponent
import javax.inject.Singleton

@Module
class DatabaseModule {

    @Provides
    @DataScope
    fun provideDatabase(context: Context, migrations: Array<Migration>): HomeAssistantDatabase {
        val builder = Room.databaseBuilder(
            context, HomeAssistantDatabase::class.java, "HomeAssistant.db"
        )
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .addMigrations(*migrations)
        if (Debug.isDebuggerConnected()) {
            builder.allowMainThreadQueries()
        }
        return builder.build()
    }

    @Provides
    fun provideMigrations(): Array<Migration> = arrayOf()

    @Provides
    fun provideWearActionDao(db: HomeAssistantDatabase) = db.wearActionsDao()

}