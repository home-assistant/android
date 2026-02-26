package io.homeassistant.companion.android

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.util.PlayServicesAvailability
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object FullApplicationModule {

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

    @Provides
    @Singleton
    internal fun providesPlayServicesAvailability(
        @ApplicationContext context: Context,
    ): PlayServicesAvailability {
        return PlayServicesAvailability {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }
    }
}
