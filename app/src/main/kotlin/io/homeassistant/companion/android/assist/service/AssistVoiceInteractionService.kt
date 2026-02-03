package io.homeassistant.companion.android.assist.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.voice.VoiceInteractionService
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListener
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_ASSIST_LISTENING
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Voice interaction service used when the app is set as the system's default assistant.
 *
 * When the user sets this app as their default assistant, this service is kept running by the
 * system. It can respond to:
 * - Assistant button presses
 * - Lock screen assistant gestures
 * - Voice commands from Bluetooth devices
 *
 * This service also manages wake word detection via [WakeWordListener]. When listening is enabled,
 * it runs as a foreground service with microphone access to detect configured wake words.
 */
class AssistVoiceInteractionService : VoiceInteractionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeWordListener = WakeWordListener(
        context = this,
        onListenerReady = ::onListenerReady,
        onWakeWordDetected = ::onWakeWordDetected,
        onListenerStopped = ::onListenerStopped,
    )
    private var lastTriggerTime: Instant? = null

    override fun onReady() {
        super.onReady()
        Timber.d("VoiceInteractionService is ready")
        // Wake word detection is not started automatically - use startListening() to enable
    }

    override fun onShutdown() {
        super.onShutdown()
        Timber.d("VoiceInteractionService is shutting down")
        stopListening()
        serviceScope.cancel()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Timber.d("Launching Assist from keyguard")
        launchAssistActivity()
    }

    /**
     * Start listening for wake words to trigger the assistant.
     *
     * Loads the first available wake word model and begins detection.
     * If already listening, this is a no-op. To change the model, call [stopListening] first.
     *
     * Requires RECORD_AUDIO permission to be granted.
     */
    @SuppressLint("MissingPermission")
    fun startListening() {
        if (wakeWordListener.isListening) {
            // TODO we might want to remove this check to let allow the user to change the model loaded or simply restart
            Timber.d("Already listening")
            return
        }

        if (!hasRecordAudioPermission()) {
            Timber.w("RECORD_AUDIO permission not granted, cannot start listening")
            return
        }

        serviceScope.launch {
            val model = loadWakeWordModel()
            wakeWordListener.start(this, model)
        }
    }

    private suspend fun loadWakeWordModel(): MicroWakeWordModelConfig {
        // TODO: Allow user to select which wake word model to use
        // TODO: Allow user to set sensibility https://github.com/esphome/home-assistant-voice-pe/blob/a379b8c5c1a35eeebc8f9925c19aab68743517a4/home-assistant-voice.yaml#L1775
        // TODO: When to start/stop listening
        val availableModels = MicroWakeWordModelConfig.loadAvailableModels(this)
        if (availableModels.isEmpty()) {
            throw IllegalStateException("No wake word models found in assets")
        }

        Timber.d("Available wake word models: ${availableModels.map { it.wakeWord }}")
        return availableModels.first()
    }

    private fun onListenerReady(model: MicroWakeWordModelConfig) {
        serviceScope.launch {
            startForegroundWithNotification(model)
        }
    }

    private fun onListenerStopped() {
        stopForegroundCompat()
    }

    /**
     * Stop listening for wake word.
     */
    fun stopListening() {
        serviceScope.launch {
            wakeWordListener.stop()
        }
    }

    private fun onWakeWordDetected(model: MicroWakeWordModelConfig) {
        val now = Clock.System.now()
        val lastTrigger = lastTriggerTime

        // Debounce: only trigger if enough time has passed since last detection
        if (lastTrigger != null && (now - lastTrigger) <= DEBOUNCE_DURATION) {
            Timber.d("Wake word detected but within debounce period, ignoring")
            return
        }

        lastTriggerTime = now
        Timber.i("Wake word '${model.wakeWord}' detected, launching Assist")
        launchAssist(wakeWord = model.wakeWord)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startForegroundWithNotification(model: MicroWakeWordModelConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(model),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification(model))
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun launchAssist(wakeWord: String? = null) {
        val args = Bundle().apply {
            wakeWord?.let { putString(EXTRA_WAKE_WORD, it) }
        }
        showSession(args, 0)
    }

    private fun createNotification(modelConfig: MicroWakeWordModelConfig): Notification {
        createNotificationChannel()

        val stopIntent = Intent(this, AssistVoiceInteractionService::class.java).apply {
            action = ACTION_STOP_LISTENING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ASSIST_LISTENING)
            .setContentTitle(getString(commonR.string.assist_listening_title))
            .setContentText(getString(commonR.string.assist_listening_wakeword, modelConfig.wakeWord))
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                commonR.drawable.ic_stat_ic_notification,
                getString(commonR.string.assist_stop_listening),
                stopPendingIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ASSIST_LISTENING,
                getString(commonR.string.assist_listening_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(commonR.string.assist_listening_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    companion object {
        private const val NOTIFICATION_ID = 9001

        private const val ACTION_START_LISTENING = "io.homeassistant.companion.android.START_LISTENING"
        private const val ACTION_STOP_LISTENING = "io.homeassistant.companion.android.STOP_LISTENING"

        /** Bundle key for passing the detected wake word phrase to the session. */
        const val EXTRA_WAKE_WORD = "wake_word"

        private val DEBOUNCE_DURATION = 3.seconds

        /**
         * Check if this VoiceInteractionService is currently the active system assistant.
         */
        fun isActiveService(context: Context): Boolean {
            val componentName = android.content.ComponentName(
                context,
                AssistVoiceInteractionService::class.java,
            )
            return isActiveService(context, componentName)
        }

        /**
         * Start listening for wake words.
         *
         * Sends an intent to start the service and begin wake word detection.
         *
         * Requires [Manifest.permission.RECORD_AUDIO] permission to access the microphone.
         */
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun startListening(context: Context) {
            val intent = Intent(context, AssistVoiceInteractionService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            context.startService(intent)
        }

        /**
         * Stop listening for wake word.
         */
        fun stopListening(context: Context) {
            val intent = Intent(context, AssistVoiceInteractionService::class.java).apply {
                action = ACTION_STOP_LISTENING
            }
            context.startService(intent)
        }
    }
}
