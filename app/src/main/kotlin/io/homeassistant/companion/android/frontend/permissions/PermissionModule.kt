package io.homeassistant.companion.android.frontend.permissions

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object PermissionModule {

    @Provides
    fun providesNotificationStatusProvider(
        @ApplicationContext context: Context,
    ): NotificationStatusProvider = NotificationStatusProvider {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
