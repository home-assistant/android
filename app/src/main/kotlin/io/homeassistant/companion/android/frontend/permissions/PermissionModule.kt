package io.homeassistant.companion.android.frontend.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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

    @Provides
    fun providesPermissionChecker(
        @ApplicationContext context: Context,
    ): PermissionChecker = PermissionChecker { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
