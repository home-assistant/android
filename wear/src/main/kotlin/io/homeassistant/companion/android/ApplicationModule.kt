package io.homeassistant.companion.android

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.PushWebsocketSupport
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    @Provides
    @Singleton
    fun providesAppVersionProviders(): AppVersionProvider {
        // Unfortunately hilt doesn't support value class yet so we need a provider
        return AppVersionProvider { AppVersion.from(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE) }
    }

    @Provides
    @Singleton
    fun providesWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    @PushWebsocketSupport
    fun providesPushWebsocketSupport(): Boolean {
        return false
    }

    @Provides
    @Singleton
    fun provideMessagingTokenProvider(): MessagingTokenProvider {
        return MessagingTokenProvider {
            return@MessagingTokenProvider MessagingToken(
                try {
                    FirebaseMessaging.getInstance().token.await()
                } catch (e: Exception) {
                    Timber.e(e, "Issue getting token")
                    ""
                },
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    @Provides
    @Singleton
    fun clock(): Clock = Clock.System
}
