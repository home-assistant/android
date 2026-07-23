package io.homeassistant.companion.android.common.sensors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.CHANNEL_SENSOR_SYNC
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.database.sensor.toSensorWithAttributes
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
import io.homeassistant.companion.android.database.server.Server
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Builds the [PendingIntent] that opens the settings screen for a sensor, used to notify the user
 * when a sensor enabled on the server is missing the runtime permission(s) it needs.
 *
 * The destination is app-specific, so each app module provides its own implementation through Hilt.
 * Returning `null` suppresses the notification.
 */
fun interface SensorSettingsIntentProvider {
    operator fun invoke(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int,
    ): PendingIntent?
}

/**
 * Runs sensor updates and reconciles sensor registrations with each configured server.
 */
@Singleton
class SensorUpdater @VisibleForTesting internal constructor(
    private val context: Context,
    private val serverManager: ServerManager,
    private val sensorRepository: SensorRepository,
    private val appVersionProvider: AppVersionProvider,
    private val managers: Set<@JvmSuppressWildcards SensorManager>,
    private val sensorSettingsIntentProvider: SensorSettingsIntentProvider,
    private val notificationManager: NotificationManager?,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        serverManager: ServerManager,
        sensorRepository: SensorRepository,
        appVersionProvider: AppVersionProvider,
        managers: Set<@JvmSuppressWildcards SensorManager>,
        sensorSettingsIntentProvider: SensorSettingsIntentProvider,
    ) : this(
        context,
        serverManager,
        sensorRepository,
        appVersionProvider,
        managers,
        sensorSettingsIntentProvider,
        context.getSystemService(),
    )

    /**
     * Requests an update from every manager that has a sensor and then syncs all servers in
     * parallel. Does nothing when no server is registered.
     *
     * [managers] and [getSensorSettingsIntent] default to the injected ones; callers that only want
     * to refresh a subset (e.g. a Bluetooth-only update with no permission notification) can override
     * them.
     */
    suspend fun updateSensors(
        intent: Intent? = null,
        getSensorSettingsIntent: SensorSettingsIntentProvider = this.sensorSettingsIntentProvider,
        managers: Set<SensorManager> = this.managers,
    ) {
        if (!serverManager.isRegistered()) {
            Timber.w("Device not registered, skipping sensor update/registration")
            return
        }

        managers.forEach { manager ->
            if (manager.hasSensor()) {
                try {
                    manager.requestSensorUpdate(intent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Issue requesting updates for ${context.getString(manager.name)}")
                }
            }
        }

        val appVersion = appVersionProvider().value
        try {
            coroutineScope {
                serverManager.servers().map { server ->
                    async { syncSensorsWithServer(server, managers, appVersion, getSensorSettingsIntent) }
                }.awaitAll()
            }
            Timber.i("Sensor updates and sync completed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Exception while awaiting sensor updates.")
        }
    }

    /**
     * Updates a single sensor identified by [sensorId] and pushes its new state to every server it
     * is registered and enabled on. No-op when no manager owns the sensor.
     */
    suspend fun updateSensor(sensorId: String) {
        val sensorManager = managers.firstOrNull {
            it.getAvailableSensors().any { s -> s.id == sensorId }
        } ?: return
        try {
            sensorManager.requestSensorUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Issue requesting updates for ${context.getString(sensorManager.name)}")
        }
        val basicSensor = sensorManager.getAvailableSensors().firstOrNull { it.id == sensorId } ?: return
        val fullSensors = sensorRepository.getFull(sensorId).toSensorsWithAttributes()
        coroutineScope {
            fullSensors.filter {
                it.sensor.enabled &&
                    it.sensor.registered == true &&
                    (it.sensor.state != it.sensor.lastSentState || it.sensor.icon != it.sensor.lastSentIcon)
            }.forEach { fullSensor ->
                launch {
                    try {
                        serverManager.integrationRepository(
                            fullSensor.sensor.serverId,
                        ).updateSensors(listOf(fullSensor.toSensorRegistration(basicSensor)))
                        sensorRepository.updateLastSentStateAndIcon(
                            basicSensor.id,
                            fullSensor.sensor.serverId,
                            fullSensor.sensor.state,
                            fullSensor.sensor.icon,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Exception while updating individual sensor.")
                    }
                }
            }
        }
    }

    private suspend fun syncSensorsWithServer(
        server: Server,
        managers: Set<SensorManager>,
        appVersion: String,
        getSensorSettingsIntent: SensorSettingsIntentProvider,
    ): Boolean {
        val config: GetConfigResponse
        val integrationRepository: IntegrationRepository

        try {
            integrationRepository = serverManager.integrationRepository(server.id)
            config = integrationRepository.getConfig()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error while getting core config to sync sensor status aborting")
            return false
        }

        val currentHAversion = integrationRepository.getHomeAssistantVersion()
        val supportsDisabledSensors = integrationRepository.isHomeAssistantVersionAtLeast(2022, 6, 0)
        val serverIsTrusted = integrationRepository.isTrusted()

        suspend fun persistRegistration(updated: Sensor): Sensor {
            val persisted = updated.copy(coreRegistration = currentHAversion, appRegistration = appVersion)
            sensorRepository.update(persisted)
            return persisted
        }

        val coreSensorStatus: Map<String, Boolean>? =
            if (supportsDisabledSensors && (serverIsTrusted || sensorRepository.getEnabledCount() > 0)) {
                config.entities
                    ?.filter { it.value["disabled"] != null }
                    ?.mapValues { !(it.value["disabled"] as Boolean) } // Map to sensor id -> enabled
            } else {
                // Cannot sync disabled, or all sensors disabled and server changes aren't trusted
                null
            }

        var serverIsReachable = true
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        managers.forEach { manager ->
            // Each manager was already asked to update in updateSensors
            val hasSensor = manager.hasSensor()

            manager.getAvailableSensors().forEach sensorForEach@{ basicSensor ->
                val hasPermission = manager.checkPermission(basicSensor.id)
                var fullSensor = loadSensorReconcilingPermission(basicSensor, server, hasPermission)
                    ?: return@sensorForEach
                val sensor = fullSensor.sensor
                val sensorCoreEnabled = coreSensorStatus?.get(basicSensor.id)
                val canBeRegistered = hasSensor &&
                    basicSensor.type.isNotBlank() &&
                    basicSensor.statelessIcon.isNotBlank()

                // Register sensor and/or update the sensor enabled state. Priority is:
                // 1. There is a new sensor or change in enabled state according to the app
                // 2. There is a change in enabled state according to core (user changed in frontend)
                // 3. There is no change in enabled state, but app/core version has changed
                if (
                    canBeRegistered &&
                    (
                        (sensor.registered == null && (sensor.enabled || supportsDisabledSensors)) ||
                            (sensor.enabled != sensor.registered && supportsDisabledSensors) ||
                            (sensor.registered != null && coreSensorStatus != null && sensorCoreEnabled == null)
                        )
                ) {
                    // 1. (Re-)register sensors with core when they can be registered and:
                    // - sensor isn't registered, but is enabled or on core >=2022.6
                    // - sensor enabled has changed from registered enabled state on core >=2022.6
                    // - sensor is registered according to database, but core >=2022.6 doesn't know about it
                    try {
                        registerSensor(fullSensor, basicSensor)
                        fullSensor =
                            fullSensor.copy(sensor = persistRegistration(sensor.copy(registered = sensor.enabled)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Issue registering sensor ${basicSensor.id}")
                    }
                } else if (
                    canBeRegistered &&
                    supportsDisabledSensors &&
                    sensorCoreEnabled != null &&
                    sensorCoreEnabled != sensor.registered
                ) {
                    // 2. Try updating the sensor enabled state to match core state when it's different from
                    // the app, if the sensor can be registered, on core >= 2022.6 and server trusted.
                    // If the server isn't trusted, update registered state to match app.
                    try {
                        // Core changed, but app doesn't trust server so 'override'
                        val sensorUpdated = if (!serverIsTrusted) {
                            registerSensor(fullSensor, basicSensor)
                            sensor
                        } else if (sensorCoreEnabled) { // App disabled, should enable
                            if (hasPermission) {
                                sensor.copy(
                                    enabled = true,
                                    registered = true,
                                )
                            } else {
                                // Can't enable due to missing permission(s), 'override' core and notify user
                                registerSensor(fullSensor, basicSensor)

                                notificationManager?.let { notificationManager ->
                                    createNotificationChannel()
                                    val notificationId = "$CHANNEL_SENSOR_SYNC-${basicSensor.id}".hashCode()
                                    val notificationIntent =
                                        getSensorSettingsIntent(context, basicSensor.id, manager.id(), notificationId)
                                    val notification = NotificationCompat.Builder(context, CHANNEL_SENSOR_SYNC)
                                        .setSmallIcon(R.drawable.ic_stat_ic_notification)
                                        .setContentTitle(context.getString(basicSensor.name))
                                        .setContentText(
                                            context.getString(R.string.sensor_worker_sync_missing_permissions),
                                        )
                                        .setContentIntent(notificationIntent)
                                        .setAutoCancel(true)
                                        .build()
                                    notificationManager.notify(notificationId, notification)
                                }
                                sensor
                            }
                        } else { // App enabled, should disable
                            sensor.copy(
                                enabled = false,
                                registered = false,
                            )
                        }

                        fullSensor = fullSensor.copy(sensor = persistRegistration(sensorUpdated))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Issue enabling/disabling sensor ${basicSensor.id}")
                    }
                } else if (
                    canBeRegistered &&
                    serverIsReachable &&
                    (sensor.enabled || supportsDisabledSensors) &&
                    (appVersion != sensor.appRegistration || currentHAversion != sensor.coreRegistration)
                ) {
                    // 3. Re-register sensors with core when they can be registered and are enabled or on
                    // core >= 2022.6, and app or core version change is detected
                    try {
                        registerSensor(fullSensor, basicSensor)
                        fullSensor =
                            fullSensor.copy(sensor = persistRegistration(sensor.copy(registered = sensor.enabled)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Issue re-registering sensor ${basicSensor.id}")
                        if (e is IntegrationException &&
                            (e.cause is ConnectException || e.cause is SocketTimeoutException)
                        ) {
                            Timber.w(
                                "Server can't be reached, skipping other registrations for sensors due to version change",
                            )
                            serverIsReachable = false
                        }
                    }
                }

                // Read from the (possibly re-registered) fullSensor so a sensor registered/enabled above
                // has its fresh state sent in this same pass rather than being deferred to the next one.
                val reconciledSensor = fullSensor.sensor
                if (canBeRegistered &&
                    reconciledSensor.enabled &&
                    reconciledSensor.registered != null &&
                    (
                        reconciledSensor.state != reconciledSensor.lastSentState ||
                            reconciledSensor.icon != reconciledSensor.lastSentIcon
                        )
                ) {
                    enabledRegistrations.add(fullSensor.toSensorRegistration(basicSensor))
                }
            }
        }

        var success = true
        if (enabledRegistrations.isNotEmpty()) {
            success = try {
                val serverSuccess = integrationRepository.updateSensors(enabledRegistrations)
                enabledRegistrations.forEach {
                    sensorRepository.updateLastSentStateAndIcon(it.uniqueId, it.serverId, it.state.toString(), it.icon)
                }
                serverSuccess
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Don't trigger re-registration when the server is down or job was cancelled
                val exceptionOk = e is IntegrationException &&
                    (e.cause is IOException)
                if (exceptionOk) {
                    Timber.w(e, "Exception while updating sensors")
                } else {
                    Timber.e(e, "Exception while updating sensors.")
                }
                exceptionOk
            }

            // We failed to update a sensor, we should re register next time
            if (!success) {
                enabledRegistrations.forEach {
                    val sensor = sensorRepository.get(it.uniqueId, it.serverId)
                    if (sensor != null) {
                        sensorRepository.update(
                            sensor.copy(
                                registered = null,
                                lastSentState = null,
                                lastSentIcon = null,
                            ),
                        )
                    }
                }
            }
        } else {
            Timber.d("Nothing to update for server ${server.id} (${server.friendlyName})")
        }
        return success
    }

    private suspend fun loadSensorReconcilingPermission(
        basicSensor: SensorManager.BasicSensor,
        server: Server,
        hasPermission: Boolean,
    ): SensorWithAttributes? {
        val fullSensor = sensorRepository.getFull(basicSensor.id, server.id).toSensorWithAttributes() ?: return null

        // A sensor enabled in the database but missing its runtime permission isn't really
        // enabled. Persist that so the reconciliation below unregisters it on the server instead
        // of leaving a stale entity behind.
        if (fullSensor.sensor.enabled && !hasPermission) {
            val disabled = fullSensor.copy(sensor = fullSensor.sensor.copy(enabled = false))
            sensorRepository.update(disabled.sensor)
            return disabled
        }
        return fullSensor
    }

    private suspend fun registerSensor(fullSensor: SensorWithAttributes, basicSensor: SensorManager.BasicSensor) {
        val reg = fullSensor.toSensorRegistration(basicSensor)
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale("en"))
        reg.name = context.createConfigurationContext(config).resources.getString(basicSensor.name)

        serverManager.integrationRepository(fullSensor.sensor.serverId).registerSensor(reg)
    }

    private fun createNotificationChannel() {
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            val manager = notificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_SENSOR_SYNC) == null) {
                val notificationChannel = NotificationChannel(
                    CHANNEL_SENSOR_SYNC,
                    CHANNEL_SENSOR_SYNC,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                manager.createNotificationChannel(notificationChannel)
            }
        }
    }
}
