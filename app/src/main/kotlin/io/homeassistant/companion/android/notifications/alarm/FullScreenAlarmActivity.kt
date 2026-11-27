package io.homeassistant.companion.android.notifications.alarm

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.BellRing
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Full-screen alarm shown for notifications sent with `full_screen_alarm: true`, similar to an incoming call or
 * an alarm clock. It is shown over the lock screen and turns the screen on.
 *
 * The screen mirrors the posted notification it belongs to: it displays its title, message and actions, and
 * closes itself as soon as the notification goes away, for example when Home Assistant sends
 * `clear_notification` for its tag, when an action is used or when the user dismisses it.
 *
 * Opening the notification target or using an action requires the user to unlock the device first, so whoever
 * holds a locked phone can only dismiss the alarm on this device.
 */
@AndroidEntryPoint
class FullScreenAlarmActivity : BaseActivity() {

    companion object {
        private const val EXTRA_NOTIFICATION_TAG = "io.homeassistant.companion.android.extra.ALARM_NOTIFICATION_TAG"
        private const val EXTRA_NOTIFICATION_ID = "io.homeassistant.companion.android.extra.ALARM_NOTIFICATION_ID"

        /** Key of the notification extra holding the [PendingIntent] opened by the alarm "Open" button. */
        const val EXTRA_OPEN_INTENT = "io.homeassistant.companion.android.extra.ALARM_OPEN_INTENT"

        /** Key of the notification extra holding the MDI icon name (like `mdi:shield-alert`) of the alarm. */
        const val EXTRA_ALARM_ICON = "io.homeassistant.companion.android.extra.ALARM_ICON"

        private val NOTIFICATION_CHECK_INTERVAL = 1.seconds

        /**
         * Number of checks to wait for the notification to appear before closing the screen, as the activity can
         * start before the system lists a notification that was just posted.
         */
        private const val MAX_CHECKS_BEFORE_POSTED = 5

        /**
         * Creates the intent showing the full-screen alarm for the posted notification identified by [tag] and
         * [notificationId].
         */
        fun newInstance(context: Context, tag: String?, notificationId: Int): Intent =
            Intent(context, FullScreenAlarmActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TAG, tag)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

    private val uiState = MutableStateFlow<FullScreenAlarmUiState?>(null)
    private val notificationTag by lazy { intent.getStringExtra(EXTRA_NOTIFICATION_TAG) }
    private val notificationId by lazy { intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0) }

    /** Picture of the notification, only loaded again when the notification is posted again. */
    private var image: ImageBitmap? = null
    private var imagePostTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O_MR1)) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)

        setContent {
            HATheme {
                val state by uiState.collectAsStateWithLifecycle()
                state?.let { currentState ->
                    FullScreenAlarmScreen(
                        uiState = currentState,
                        onDismiss = ::dismissAlarm,
                        onOpen = ::openNotificationTarget,
                        onActionClick = ::runAction,
                        onAllowFullScreen = ::openFullScreenIntentSettings,
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { followNotification() }
        }
    }

    /** Keeps the screen in sync with the notification and closes it once the notification is gone. */
    private suspend fun followNotification() {
        var hasBeenPosted = false
        var checksWithoutNotification = 0
        while (true) {
            val notification = findNotification()
            if (notification != null) {
                hasBeenPosted = true
                if (notification.postTime != imagePostTime) {
                    imagePostTime = notification.postTime
                    val extras = notification.notification.extras
                    image = withContext(Dispatchers.IO) { extras.loadPicture(this@FullScreenAlarmActivity) }
                }
                uiState.value = notification.toUiState()
            } else {
                checksWithoutNotification++
                if (hasBeenPosted || checksWithoutNotification >= MAX_CHECKS_BEFORE_POSTED) {
                    finish()
                    return
                }
            }
            delay(NOTIFICATION_CHECK_INTERVAL)
        }
    }

    private fun findNotification(): StatusBarNotification? =
        getSystemService<NotificationManager>()?.activeNotifications?.firstOrNull {
            it.tag == notificationTag && it.id == notificationId
        }

    private fun StatusBarNotification.toUiState(): FullScreenAlarmUiState {
        val extras = notification.extras
        val isDeviceLocked = getSystemService<KeyguardManager>()?.isKeyguardLocked == true
        // Before Android O the device cannot be unlocked from here, so hide the controls requiring it while locked
        val controlsAvailable = !isDeviceLocked || SdkVersion.isAtLeast(Build.VERSION_CODES.O)
        return FullScreenAlarmUiState(
            icon = extras.getString(EXTRA_ALARM_ICON)?.let { Mdi.fromHaName(it) } ?: Mdi.BellRing,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            message = (
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                )?.toString().orEmpty(),
            canOpen = controlsAvailable && getOpenIntent() != null,
            // Actions asking for a text reply need the notification shade instead
            actions = if (controlsAvailable) {
                notification.actions.orEmpty().mapIndexedNotNull { index, action ->
                    FullScreenAlarmAction(index, action.title.toString()).takeIf { action.remoteInputs.isNullOrEmpty() }
                }
            } else {
                emptyList()
            },
            isFullScreenIntentAllowed = !SdkVersion.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ||
                getSystemService<NotificationManager>()?.canUseFullScreenIntent() != false,
            image = image,
        )
    }

    private fun StatusBarNotification.getOpenIntent(): PendingIntent? =
        BundleCompat.getParcelable(notification.extras, EXTRA_OPEN_INTENT, PendingIntent::class.java)

    private fun dismissAlarm() {
        NotificationManagerCompat.from(this).cancel(notificationTag, notificationId)
        finish()
    }

    private fun openNotificationTarget() {
        val openIntent = findNotification()?.getOpenIntent() ?: return
        runAfterUnlock {
            sendPendingIntent(openIntent)
            dismissAlarm()
        }
    }

    private fun runAction(action: FullScreenAlarmAction) {
        val notificationAction = findNotification()?.notification?.actions?.getOrNull(action.index) ?: return
        runAfterUnlock { sendPendingIntent(notificationAction.actionIntent) }
    }

    private fun sendPendingIntent(pendingIntent: PendingIntent) {
        try {
            pendingIntent.send()
        } catch (e: PendingIntent.CanceledException) {
            Timber.w(e, "Unable to send full-screen alarm pending intent")
        }
    }

    private fun openFullScreenIntentSettings() {
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            runAfterUnlock {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()),
                )
            }
        }
    }

    /**
     * Runs [block] once the device is unlocked, asking the user to unlock it first if needed. Before Android O
     * there is no way to request it, so the screen hides the controls requiring it while the device is locked.
     */
    private fun runAfterUnlock(block: () -> Unit) {
        val keyguardManager = getSystemService<KeyguardManager>()
        if (keyguardManager?.isKeyguardLocked != true) {
            block()
        } else if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = block()
                },
            )
        }
    }
}

/**
 * Loads the picture of the notification's big picture style, set from its `image` option. Android 12+ keeps
 * animated pictures as an icon pointing to a file, and other pictures as a bitmap.
 */
private fun Bundle.loadPicture(context: Context): ImageBitmap? {
    val pictureIcon = BundleCompat.getParcelable(this, NotificationCompat.EXTRA_PICTURE_ICON, Icon::class.java)
    val picture = pictureIcon?.loadDrawable(context)?.toBitmap()
        ?: BundleCompat.getParcelable(this, NotificationCompat.EXTRA_PICTURE, Bitmap::class.java)
    return picture?.asImageBitmap()
}
