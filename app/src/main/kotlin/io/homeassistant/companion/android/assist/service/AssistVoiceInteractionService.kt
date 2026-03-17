package io.homeassistant.companion.android.assist.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService.Companion.isActiveService
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListener
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_ASSIST_LISTENING
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
@AndroidEntryPoint
class AssistVoiceInteractionService : VoiceInteractionService() {
    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var assistConfigManager: AssistConfigManager

    @Inject
    lateinit var wakeWordListenerFactory: WakeWordListenerFactory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val wakeWordListener: WakeWordListener by lazy {
        wakeWordListenerFactory.create(
            onWakeWordDetected = ::onWakeWordDetected,
            onListenerReady = ::onListenerReady,
            onListenerStopped = ::onListenerStopped,
            onListenerFailed = ::onListenerFailed,
        )
    }
    private var lastTriggerTime: Instant? = null
    private var isServiceReady = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleAction(intent.action)
        }
    }

    override fun onReady() {
        super.onReady()
        isServiceReady = true
        Timber.d("VoiceInteractionService is ready")
        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            IntentFilter().apply {
                addAction(ACTION_START_LISTENING)
                addAction(ACTION_STOP_LISTENING)
                addAction(ACTION_RESUME_LISTENING)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        serviceScope.launch {
            if (assistConfigManager.isWakeWordEnabled()) {
                Timber.d("Wake word detection is enabled, starting listener")
                startListening()
            } else {
                Timber.d("Wake word detection is disabled")
            }
        }
    }

    override fun onShutdown() {
        super.onShutdown()
        isServiceReady = false
        Timber.d("VoiceInteractionService is shutting down")
        unregisterReceiver(commandReceiver)
        // Don't use stopListening() as it launches a coroutine that may not complete before cancel
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Fallback for commands delivered via startService() when the service is already running
        handleAction(intent?.action)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Timber.d("Launching Assist from keyguard")
        launchAssist()
    }

    private fun handleAction(action: String?) {
        when (action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_RESUME_LISTENING -> resumeListening()
        }
    }

    /**
     * Start listening for wake words to trigger the assistant.
     *
     * Loads the currently selected wake word model and begins detection.
     * If already listening, the current listener is stopped and restarted with the
     * currently selected model. Call this method after changing the wake word selection
     * to apply the new model.
     *
     * Requires RECORD_AUDIO permission to be granted.
     */
    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (!assistConfigManager.isWakeWordSupported()) {
            Timber.d("Wake word detection is not supported on this device")
            return
        }
        if (!hasRecordAudioPermission()) {
            Timber.w("RECORD_AUDIO permission not granted, cannot start listening")
            return
        }

        serviceScope.launch {
            wakeWordListener.stop()
            val model = loadWakeWordModel()
            wakeWordListener.start(this, model)
        }
    }

    private suspend fun loadWakeWordModel(): MicroWakeWordModelConfig {
        val selectedModel = assistConfigManager.getSelectedWakeWordModel()
        if (selectedModel != null) {
            Timber.d("Using selected wake word model: ${selectedModel.wakeWord}")
            return selectedModel
        }

        // Fall back to first available model if none selected
        val availableModels = assistConfigManager.getAvailableModels()
        if (availableModels.isEmpty()) {
            throw IllegalStateException("No wake word models found in assets")
        }

        val fallbackModel = availableModels.first()
        Timber.d("No model selected, using fallback: ${fallbackModel.wakeWord}")
        return fallbackModel
    }

    private fun onListenerReady(model: MicroWakeWordModelConfig) {
        serviceScope.launch {
            startForegroundWithNotification(model)
        }
    }

    private fun onListenerStopped() {
        stopForegroundCompat()
    }

    private fun onListenerFailed() {
        serviceScope.launch {
            Timber.w("Wake word listener failed, disabling wake word to prevent issue")
            @SuppressLint("MissingPermission")
            assistConfigManager.setWakeWordEnabled(false)
        }
    }

    /**
     * Stop listening for wake word.
     */
    private fun stopListening() {
        serviceScope.launch {
            wakeWordListener.stop()
        }
    }

    /**
     * Resume wake word listening if it is still enabled in settings.
     */
    private fun resumeListening() {
        serviceScope.launch {
            if (assistConfigManager.isWakeWordEnabled()) {
                startListening()
            }
        }
    }

    private fun onWakeWordDetected(model: MicroWakeWordModelConfig) {
        // Always broadcast for observers (e.g. settings test mode) regardless of debounce
        sendBroadcast(
            Intent(ACTION_WAKE_WORD_DETECTED).setPackage(packageName),
        )

        val now = clock.now()
        val lastTrigger = lastTriggerTime

        // Debounce: only trigger if enough time has passed since last detection
        if (lastTrigger != null && (now - lastTrigger) <= DEBOUNCE_DURATION) {
            Timber.d("Wake word detected but within debounce period, ignoring")
            return
        }

        lastTriggerTime = now
        Timber.i("Wake word '${model.wakeWord}' detected, launching Assist")

        // Stop the listener before launching Assist to release the microphone.
        // Before Android 10 (API 29), only one component can hold an AudioRecord at a time.
        // See https://developer.android.com/media/platform/sharing-audio-input
        serviceScope.launch {
            wakeWordListener.stop()
            launchAssist(wakeWord = model.wakeWord)
        }
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
        if (!isServiceReady) {
            Timber.w("Cannot launch Assist: VoiceInteractionService is not ready yet")
            return
        }
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

    companion object {
        private const val NOTIFICATION_ID = 9001

        @VisibleForTesting
        const val ACTION_START_LISTENING = "io.homeassistant.companion.android.START_LISTENING"

        @VisibleForTesting
        const val ACTION_STOP_LISTENING = "io.homeassistant.companion.android.STOP_LISTENING"

        @VisibleForTesting
        const val ACTION_RESUME_LISTENING = "io.homeassistant.companion.android.RESUME_LISTENING"

        /** Bundle key for passing the detected wake word phrase to the session. */
        const val EXTRA_WAKE_WORD = "wake_word"

        private const val ACTION_WAKE_WORD_DETECTED = "io.homeassistant.companion.android.WAKE_WORD_DETECTED"

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
         * Sends a package-scoped broadcast to the service to begin wake word detection.
         * If already listening, the current listener is stopped and restarted with the
         * currently selected wake word model. Call this method after changing the wake word
         * selection to apply the new model.
         *
         * Requires [Manifest.permission.RECORD_AUDIO] permission to access the microphone.
         */
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun startListening(context: Context) {
            broadcastAction(context, ACTION_START_LISTENING)
        }

        /**
         * Stop listening for wake word.
         */
        fun stopListening(context: Context) {
            broadcastAction(context, ACTION_STOP_LISTENING)
        }

        /**
         * Resume wake word listening if it is still enabled in settings.
         */
        fun resumeListening(context: Context) {
            broadcastAction(context, ACTION_RESUME_LISTENING)
        }

        /**
         * Sends a package-scoped broadcast to communicate with the service.
         *
         * Unlike [Context.startService], broadcasts are not subject to background
         * execution restrictions on Android 8+, making them safe to send from anywhere
         * including FCM/WebSocket callbacks and [android.app.Activity.onDestroy].
         *
         * Note: The broadcast is only delivered to [AssistVoiceInteractionService] while it is
         * running and its internal broadcast receiver is registered (after the service is ready).
         * If the service is not running or not yet ready when this is called, the broadcast will
         * be silently dropped. Callers that require guaranteed delivery should ensure the service
         * is active before invoking this method using [isActiveService].
         */
        private fun broadcastAction(context: Context, action: String) {
            context.sendBroadcast(
                Intent(action).setPackage(context.packageName),
            )
        }

        /**
         * Returns a [Flow] that emits each time a wake word is detected by the service.
         *
         * Internally registers a package-scoped [BroadcastReceiver] for the detection
         * broadcast and unregisters it when the flow collection is cancelled.
         */
        @SuppressLint("WrongConstant")
        fun wakeWordDetections(context: Context): Flow<Unit> = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    trySend(Unit)
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_WAKE_WORD_DETECTED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            awaitClose {
                context.unregisterReceiver(receiver)
            }
        }
    }
}
