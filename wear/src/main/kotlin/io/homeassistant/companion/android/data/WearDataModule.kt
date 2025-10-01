package io.homeassistant.companion.android.data

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.di.OkHttpConfigurator
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object WearDataModule {
    @Provides
    @IntoSet
    @Singleton
    fun bindOkHttpClientConfigurator(wearDns: WearDns): OkHttpConfigurator = object : OkHttpConfigurator {
        override fun invoke(builder: OkHttpClient.Builder) {
            builder.dns(wearDns)
        }
    }

    @Provides
    fun bindMessageClient(@ApplicationContext context: Context): MessageClient = Wearable.getMessageClient(context)

    @Provides
    fun bindCapabilityClient(@ApplicationContext context: Context): CapabilityClient =
        Wearable.getCapabilityClient(context)
}
