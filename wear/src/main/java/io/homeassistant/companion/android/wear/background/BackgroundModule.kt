package io.homeassistant.companion.android.wear.background

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides

@Module
class BackgroundModule {

    @Provides
    fun provideCapabilityClient(context: Context): CapabilityClient {
        return Wearable.getCapabilityClient(context)
    }

    @Provides
    fun provideMessageClient(context: Context): MessageClient {
        return Wearable.getMessageClient(context)
    }
}
