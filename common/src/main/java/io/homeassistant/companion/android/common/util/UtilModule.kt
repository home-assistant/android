package io.homeassistant.companion.android.common.util

import android.content.Context
import android.media.AudioManager
import androidx.core.content.getSystemService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.feature.BooleanSampleFeature
import io.homeassistant.companion.android.common.util.feature.DefaultFeatureValue
import io.homeassistant.companion.android.common.util.feature.FeatureValue
import io.homeassistant.companion.android.common.util.feature.FeatureValuesStore
import io.homeassistant.companion.android.common.util.feature.FeaturesStoreImpl
import io.homeassistant.companion.android.common.util.feature.ImmutableStringSampleFeature
import io.homeassistant.companion.android.common.util.feature.LocalFeatureValue
import io.homeassistant.companion.android.common.util.feature.StringSampleFeature
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UtilModule {

    companion object {
        @Provides
        @Singleton
        fun provideAudioRecorder(@ApplicationContext appContext: Context): AudioRecorder =
            AudioRecorder(appContext.getSystemService<AudioManager>())

        @Provides
        @Singleton
        fun provideAudioUrlPlayer(@ApplicationContext appContext: Context): AudioUrlPlayer =
            AudioUrlPlayer(appContext.getSystemService<AudioManager>())

        @Provides
        @Singleton
        @Named("features")
        fun provideLocalStorage(@ApplicationContext appContext: Context): LocalStorage {
            return LocalStorageImpl(
                appContext.getSharedPreferences(
                    "features",
                    Context.MODE_PRIVATE
                )
            )
        }

        // Inject a sample into the set of feature for demo purpose
        @Provides
        @Singleton
        @IntoSet
        fun provideBooleanSampleFeature(@Named("features") localStorage: LocalStorage): FeatureValue<*> {
            return LocalFeatureValue(localStorage, BooleanSampleFeature)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideStringSampleFeature(@Named("features") localStorage: LocalStorage): FeatureValue<*> {
            return LocalFeatureValue(localStorage, StringSampleFeature)
        }

        @Provides
        @Singleton
        @IntoSet
        fun provideImmutableStringSampleFeature(): FeatureValue<*> {
            return DefaultFeatureValue(ImmutableStringSampleFeature)
        }
    }

    @Binds
    @Singleton
    internal abstract fun provideFeaturesStore(impl: FeaturesStoreImpl): FeatureValuesStore
}
