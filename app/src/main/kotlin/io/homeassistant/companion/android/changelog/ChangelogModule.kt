package io.homeassistant.companion.android.changelog

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.getSharedPreferencesSuspend
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedChangelogStorage

@Module
@InstallIn(SingletonComponent::class)
object ChangelogModule {

    @Provides
    @Singleton
    @NamedChangelogStorage
    fun provideChangelogLocalStorage(@ApplicationContext appContext: Context): LocalStorage = LocalStorageImpl {
        appContext.getSharedPreferencesSuspend(CHANGELOG_PREFERENCES_NAME)
    }
}
