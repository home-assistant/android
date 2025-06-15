package io.homeassistant.companion.android.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.data.OkHttpConfigurator
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
}
