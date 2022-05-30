package io.homeassistant.companion.android.database

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    fun provideAuthenticationDao(database: AppDatabase) = database.authenticationDao()

    @Provides
    fun provideSensorDao(database: AppDatabase) = database.sensorDao()

    @Provides
    fun provideButtonWidgetDao(database: AppDatabase) = database.buttonWidgetDao()

    @Provides
    fun provideCameraWidgetDao(database: AppDatabase) = database.cameraWidgetDao()

    @Provides
    fun provideMediaPlayCtrlWidgetDao(database: AppDatabase) = database.mediaPlayCtrlWidgetDao()

    @Provides
    fun provideStaticWidgetDao(database: AppDatabase) = database.staticWidgetDao()

    @Provides
    fun provideTemplateWidgetDao(database: AppDatabase) = database.templateWidgetDao()

    @Provides
    fun provideNotificationDao(database: AppDatabase) = database.notificationDao()

    @Provides
    fun provideTileDao(database: AppDatabase) = database.tileDao()

    @Provides
    fun provideFavoritesDao(database: AppDatabase) = database.favoritesDao()

    @Provides
    fun provideSettingsDao(database: AppDatabase) = database.settingsDao()
}
