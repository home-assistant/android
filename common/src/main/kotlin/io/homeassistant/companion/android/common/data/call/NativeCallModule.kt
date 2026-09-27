package io.homeassistant.companion.android.common.data.call

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Blocking native audio and call descriptor work. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NativeCallIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object NativeCallModule {
    // The DI boundary selects the real dispatcher; consumers receive it through injection.
    @Suppress("InjectDispatcher")
    @Provides
    @NativeCallIoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
