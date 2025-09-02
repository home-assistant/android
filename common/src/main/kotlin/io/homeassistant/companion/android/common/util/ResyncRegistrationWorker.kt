package io.homeassistant.companion.android.common.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.PushWebsocketSupport
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A worker that will resync the device registration with all connected servers.
 * This is used to ensure that the device registration is up to date with the latest app version
 * and push token. It also updates retrieves the config and current user to update the local cache.
 */
class ResyncRegistrationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context.applicationContext, params) {

    companion object {

        /**
         * We could use androidx hilt compiler and work library to avoid having to deal with the entry point,
         * but there is an ongoing bug that cause a StackOverflow when we apply the compiler to the project that
         * prevent us from using it at the moment https://github.com/google/dagger/issues/4702.
         */
        @EntryPoint
        @InstallIn(SingletonComponent::class)
        internal interface ResyncRegistrationWorkerEntryPoint {
            fun serverManager(): ServerManager
            fun appVersionProvider(): AppVersionProvider
            fun pushToken(): MessagingTokenProvider

            @PushWebsocketSupport
            fun pushWebsocketSupport(): Boolean
        }

        fun WorkManager.enqueueResyncRegistration() {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()

            val worker = OneTimeWorkRequestBuilder<ResyncRegistrationWorker>()
                .setConstraints(constraints)
                .build()

            enqueue(worker)
        }
    }

    override suspend fun doWork(): Result {
        return coroutineScope {
            val entryPoints = EntryPoints.get(applicationContext, ResyncRegistrationWorkerEntryPoint::class.java)
            val serverManager = entryPoints.serverManager()

            if (!serverManager.isRegistered()) return@coroutineScope Result.failure()

            var result = Result.success()
            serverManager.defaultServers.map {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).apply {
                            updateRegistration(
                                DeviceRegistration(
                                    appVersion = entryPoints.appVersionProvider()(),
                                    pushToken = entryPoints.pushToken()(),
                                    pushWebsocket = entryPoints.pushWebsocketSupport(),
                                ),
                            )
                            getConfig() // Update cached data
                        }
                        serverManager.webSocketRepository(it.id)
                            .getCurrentUser() // Update cached data
                    } catch (e: Exception) {
                        Timber.e(e, "Issue updating Registration")
                        result = Result.failure()
                    }
                }
            }.joinAll()

            result
        }
    }
}
