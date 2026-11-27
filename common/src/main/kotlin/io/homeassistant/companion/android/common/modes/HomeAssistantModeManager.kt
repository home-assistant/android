package io.homeassistant.companion.android.common.modes

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val CONDITION_PATH = "home_assistant_mode"
private const val CONDITION_SUMMARY = ""

/**
 * The "Home Assistant" mode that the user can add to Android's Modes from the app's settings.
 *
 * Unlike the mode Android creates on its own for [NotificationManager.setInterruptionFilter], the user can turn this
 * one on and off from Android.
 *
 * The mode is only added and removed on the user's request, see [add] and [remove]; turning it on and off never adds
 * it. It is found by its condition id, so nothing about it is stored in the app. Only the first call to [isSupported]
 * talks to Android on the caller's thread; the other functions switch to an IO dispatcher and are safe to call from
 * the main thread.
 */
@Singleton
class HomeAssistantModeManager @VisibleForTesting internal constructor(
    private val context: Context,
    private val notificationManager: NotificationManager?,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        notificationManager = context.getSystemService<NotificationManager>(),
        ioDispatcher = Dispatchers.IO,
    )

    /**
     * What Android knows about the mode.
     *
     * @property added whether the mode is in Android's Modes
     * @property enabled whether the user left the mode enabled in Android, which ignores every change to a disabled
     * mode; `false` when the mode is not added
     * @property active whether the mode is on, `false` when it is not added, `null` when Android cannot tell
     */
    data class State(val added: Boolean, val enabled: Boolean, val active: Boolean?)

    /** Outcome of [setActive]. */
    sealed interface ChangeResult {
        /** The mode is now in the requested state. */
        data object Applied : ChangeResult

        /** Android accepted the change but the mode is not in the requested state, or Android cannot tell its state. */
        data object NotApplied : ChangeResult

        /** The mode is not added, see [add]. */
        data object NotAdded : ChangeResult

        /** Do Not Disturb access is missing, so the app can neither see nor change the mode, see [hasAccess]. */
        data object NoAccess : ChangeResult

        /** The user disabled the mode in Android, which then ignores every change until they enable it again. */
        data object DisabledInAndroid : ChangeResult

        /** The device does not support the mode, or Android refused the change (logged). */
        data object Failed : ChangeResult
    }

    /** Serializes every change to the mode, so it cannot be added twice or changed while being removed. */
    private val mutex = Mutex()

    private val conditionId: Uri by lazy {
        Uri.Builder()
            .scheme(Condition.SCHEME)
            .authority(context.packageName)
            .appendPath(CONDITION_PATH)
            .build()
    }

    private val supported: Boolean by lazy {
        SdkVersion.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM) &&
            notificationManager?.areAutomaticZenRulesUserManaged() == true
    }

    /**
     * @return `true` if this device lets the user turn a mode owned by an app on and off: Android 15 or newer with
     * the Modes UI, never on watches, cars or TVs.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun isSupported(): Boolean = supported

    /** @return `true` if the user granted the Do Not Disturb access required to manage the mode. */
    suspend fun hasAccess(): Boolean = withContext(ioDispatcher) {
        notificationManager?.isNotificationPolicyAccessGranted == true
    }

    /**
     * @return the state of the mode, or `null` when it cannot be read: the device does not support the mode, or Do Not
     * Disturb access is missing (see [hasAccess])
     */
    suspend fun getState(): State? {
        if (!isSupported()) return null
        return withNotificationManager("read") { manager ->
            val (id, rule) = manager.findModes().entries.firstOrNull()
                ?: return@withNotificationManager State(added = false, enabled = false, active = false)
            State(added = true, enabled = rule.isEnabled, active = manager.isModeActive(id))
        }
    }

    /**
     * Adds the mode to Android's Modes, unless it is already there. Requires Do Not Disturb access, see [hasAccess].
     *
     * @return `true` if the mode is in Android's Modes afterwards
     */
    suspend fun add(): Boolean {
        if (!isSupported()) return false
        return withNotificationManager("add") { manager -> manager.findModes().isNotEmpty() || manager.addMode() } ==
            true
    }

    /**
     * Removes the mode from Android's Modes. Android can keep the settings the user made for it, and bring them back
     * when the mode is added again. Requires Do Not Disturb access, see [hasAccess].
     *
     * @return `true` if the mode is not in Android's Modes afterwards
     */
    suspend fun remove(): Boolean {
        if (!isSupported()) return true
        return withNotificationManager("remove") { manager ->
            // All of them, in case an earlier version of the app added more than one, even if one fails
            manager.findModes().keys.map { id ->
                manager.removeAutomaticZenRule(id).also { removed ->
                    if (!removed) Timber.w("Android did not remove the Home Assistant mode $id")
                }
            }.all { removed -> removed }
        } == true
    }

    /**
     * Turns the mode on or off, when it is added.
     *
     * The change applies like tapping the mode on the device, whatever the user did by hand before: the command is the
     * user's own instruction relayed by their Home Assistant, so it is sent as [Condition.SOURCE_USER_ACTION], for which
     * Android drops the user's manual override instead of keeping it. An automatic change would silently do nothing
     * while such an override is active, which nobody can see.
     */
    suspend fun setActive(active: Boolean): ChangeResult {
        if (!isSupported()) return ChangeResult.Failed
        return if (hasAccess()) changeActive(active) else ChangeResult.NoAccess
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun changeActive(active: Boolean): ChangeResult =
        withNotificationManager(if (active) "turn on" else "turn off") { manager ->
            val (id, rule) = manager.findModes().entries.firstOrNull()
                ?: return@withNotificationManager ChangeResult.NotAdded
            if (!rule.isEnabled) return@withNotificationManager ChangeResult.DisabledInAndroid
            val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
            manager.setAutomaticZenRuleState(
                id,
                Condition(conditionId, CONDITION_SUMMARY, state, Condition.SOURCE_USER_ACTION),
            )
            if (manager.isModeActive(id) == active) ChangeResult.Applied else ChangeResult.NotApplied
        } ?: ChangeResult.Failed

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun <T : Any> withNotificationManager(
        action: String,
        block: suspend (NotificationManager) -> T?,
    ): T? {
        val manager = notificationManager ?: return null
        return withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    // Android refuses all of this without the access, which most users never grant: not worth a log
                    if (manager.isNotificationPolicyAccessGranted) block(manager) else null
                } catch (e: SecurityException) {
                    Timber.w(e, "Failed to $action the Home Assistant mode, Do Not Disturb access was just revoked")
                    null
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Failed to $action the Home Assistant mode for conditionId=$conditionId")
                    null
                }
            }
        }
    }

    /**
     * @return the app's modes with the condition id of the Home Assistant mode, by id: one once added, more only if
     * an earlier version of the app added it twice
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun NotificationManager.findModes(): Map<String, AutomaticZenRule> =
        automaticZenRules.filterValues { it.conditionId == conditionId }

    /**
     * Adds the mode. Android links to an activity from the mode's page: prefers the app's screen for the mode, declared
     * with [NotificationManager.ACTION_AUTOMATIC_ZEN_RULE], over the launcher activity.
     *
     * @return `true` if Android added the mode
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun NotificationManager.addMode(): Boolean {
        val packageManager = context.packageManager
        val configurationActivity = packageManager.resolveActivity(
            Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE).setPackage(context.packageName),
            PackageManager.ResolveInfoFlags.of(0),
        )?.activityInfo?.let { ComponentName(it.packageName, it.name) }
            ?: packageManager.getLaunchIntentForPackage(context.packageName)?.component
        if (configurationActivity == null) {
            Timber.w("Cannot add the Home Assistant mode without an activity to link to")
            return false
        }
        val rule = AutomaticZenRule.Builder(context.getString(commonR.string.app_name), conditionId)
            .setConfigurationActivity(configurationActivity)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setManualInvocationAllowed(true)
            .build()
        return (addAutomaticZenRule(rule) != null).also { added ->
            if (!added) Timber.w("Android did not add the Home Assistant mode")
        }
    }
}

/** @return whether the mode is on, or `null` when Android cannot tell, for example because it was just removed */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun NotificationManager.isModeActive(id: String): Boolean? = when (getAutomaticZenRuleState(id)) {
    Condition.STATE_TRUE -> true
    Condition.STATE_FALSE -> false
    else -> null
}
