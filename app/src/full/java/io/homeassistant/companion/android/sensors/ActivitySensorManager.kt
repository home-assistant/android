package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    companion object {

        internal const val TAG = "ActivitySM"

        const val ACTION_REQUEST_ACTIVITY_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_ACTIVITY_UPDATES"
        const val ACTION_UPDATE_ACTIVITY =
            "io.homeassistant.companion.android.background.UPDATE_ACTIVITY"

        fun restartActivityTracking(context: Context) {
            val intent = Intent(context, ActivitySensorManager::class.java)
            intent.action = ACTION_REQUEST_ACTIVITY_UPDATES

            context.sendBroadcast(intent)
        }

        private val activity = SensorManager.BasicSensor(
            "detected_activity",
            "sensor",
            "Detected Activity"
        )

        private var stored_activity: String = "unknown"
        private var stored_attributes = mutableMapOf<String, Int>()
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        ensureInjected(context)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_REQUEST_ACTIVITY_UPDATES -> setupActivityTracking(context)
            ACTION_UPDATE_ACTIVITY -> handleActivityUpdate(intent, context)
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerSensorComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }

    private fun setupActivityTracking(context: Context) {
        if (!checkPermission(context)) {
            Log.w(TAG, "Not starting activity tracking because of permissions.")
            return
        }

        val sensorDao = AppDatabase.getInstance(context).sensorDao()

        ioScope.launch {
            try {
                Log.d(TAG, "Unregistering for activity updates.")
                ActivityRecognition.getClient(context).removeActivityUpdates(getPendingIntent(context))

                if (sensorDao.get(activity.id)?.enabled == true) {
                    Log.d(TAG, "Registering for activity updates.")

                    ActivityRecognition.getClient(context).requestActivityUpdates(120000, getPendingIntent(context))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Issue setting up activity tracking", e)
            }
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivitySensorManager::class.java)
        intent.action = ACTION_UPDATE_ACTIVITY
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun handleActivityUpdate(intent: Intent, context: Context) {
        Log.d(TAG, "Received activity update.")
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            var probActivity = typeToString(result.mostProbableActivity)

            if (probActivity == "on_foot")
                probActivity = getSubActivity(result)

            if (stored_activity != probActivity) {
                stored_activity = probActivity

                stored_attributes.clear()
                for (act in result.probableActivities)
                    stored_attributes[typeToString(act)] = act.confidence

                val intent = Intent(context, SensorReceiver::class.java)
                intent.action = SensorReceiver.ACTION_REQUEST_SENSORS_UPDATE
                context.sendBroadcast(intent)
            }
        }
    }

    private fun typeToString(activity: DetectedActivity): String {
        return when (activity.type) {
            DetectedActivity.IN_VEHICLE -> "in_vehicle"
            DetectedActivity.ON_BICYCLE -> "on_bicycle"
            DetectedActivity.ON_FOOT -> "on_foot"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.STILL -> "still"
            DetectedActivity.TILTING -> "tilting"
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.UNKNOWN -> "unknown"
            else -> "unknown"
        }
    }

    private fun getSubActivity(result: ActivityRecognitionResult): String {
        if (result.probableActivities[1].type == DetectedActivity.RUNNING) return "running"
        if (result.probableActivities[1].type == DetectedActivity.WALKING) return "walking"
        return "on_foot"
    }

    override val name: String
        get() = "Activity Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(activity)

    override fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf()
        }
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            activity.id -> getActivitySensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getActivitySensor(context: Context): SensorRegistration<Any> {

        return activity.toSensorRegistration(
            stored_activity,
            getSensorIcon(stored_activity),
            stored_attributes
        )
    }
    private fun getSensorIcon(activity: String): String {

        return when (activity) {
            "in_vehicle" -> "mdi:car"
            "on_bicycle" -> "mdi:bike"
            "on_foot" -> "mdi:shoe-print"
            "still" -> "mdi:sleep"
            "tilting" -> "mdi:phone-rotate-portrait"
            "walking" -> "mdi:walk"
            "running" -> "mdi:run"
            else -> "mdi:progress-question"
        }
    }
}
