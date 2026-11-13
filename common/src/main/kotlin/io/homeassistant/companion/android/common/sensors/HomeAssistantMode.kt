package io.homeassistant.companion.android.common.sensors

import android.annotation.SuppressLint
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ConditionProviderService
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.di.qualifiers.NamedHomeAssistantModeStorage
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
private const val PREF_MODE_ID = "mode_id"

/**
 * The "Home Assistant" mode that the app can add to Android's Modes.
 *
 * Unlike the mode Android creates on its own for [NotificationManager.setInterruptionFilter], the user can turn this
 * one on and off from Android, and Android tells the app when it changes with
 * [NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED].
 *
 * While its sensor is enabled, the id of the mode the app added is stored, so the app can tell a mode the user removed
 * from one it never added, even after a restart. Only the first call to [isSupported] talks to Android on the caller's
 * thread; the other functions switch to an IO dispatcher and are safe to call from the main thread.
 */
@Singleton
class HomeAssistantMode @VisibleForTesting internal constructor(
    private val context: Context,
    private val notificationManager: NotificationManager?,
    private val storage: LocalStorage,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @NamedHomeAssistantModeStorage storage: LocalStorage,
    ) : this(
        context = context,
        notificationManager = context.getSystemService<NotificationManager>(),
        storage = storage,
        ioDispatcher = Dispatchers.IO,
    )

    /** Outcome of [setActive]. */
    sealed interface ChangeResult {
        /** The mode is now in the requested state. */
        data object Applied : ChangeResult

        /** Android accepted the change but the mode is not in the requested state, or Android cannot tell its state. */
        data object NotApplied : ChangeResult

        /** There is no mode to turn off. */
        data object NotAdded : ChangeResult

        /** The user disabled the mode in Android, which then ignores every change until they enable it again. */
        data object DisabledInAndroid : ChangeResult

        /** The device does not support the mode, or Android refused the change (logged). */
        data object Failed : ChangeResult
    }

    /** What [findMode] knows about the mode the app added. */
    private sealed interface Lookup {
        /** The mode is in Android: its [id] and the [rule] Android keeps for it. */
        data class Found(val id: String, val rule: AutomaticZenRule) : Lookup

        /** The app added the mode and it is gone, so the user removed it. */
        data object RemovedByUser : Lookup

        /** The app did not add the mode, or no longer tracks it. */
        data object NotAdded : Lookup
    }

    /** Serializes every decision about the mode, so it cannot be added twice or added back while being removed. */
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
    internal suspend fun hasAccess(): Boolean = withContext(ioDispatcher) {
        notificationManager?.isNotificationPolicyAccessGranted == true
    }

    /**
     * @return `true` if the device supports the mode and [intent] does not name a mode other than the app's: Android
     * passes the mode's id with [NotificationManager.EXTRA_AUTOMATIC_RULE_ID] or [ConditionProviderService.EXTRA_RULE_ID].
     * A launch without an id is accepted, since some Android versions may not pass it and the screen it opens is
     * harmless.
     */
    @SuppressLint("InlinedApi")
    suspend fun isOwnModeLaunch(intent: Intent): Boolean {
        if (!isSupported()) return false
        val ids = listOfNotNull(
            intent.getStringExtra(NotificationManager.EXTRA_AUTOMATIC_RULE_ID),
            intent.getStringExtra(ConditionProviderService.EXTRA_RULE_ID),
        )
        return ids.isEmpty() ||
            withNotificationManager("check") { manager -> (manager.findMode() as? Lookup.Found)?.id in ids } == true
    }

    /**
     * Reads whether the mode is on for its sensor, which [isWanted] tells is enabled, adding the mode when the app did
     * not add it yet. When the user removed it while the sensor was enabled, [onRemovedByUser] disables the sensor
     * instead, so the mode is only added again once the sensor is enabled again. Both run while no other change to the
     * mode can happen.
     *
     * @return whether the mode is on, or `null` when there is no state to report
     */
    internal suspend fun update(isWanted: suspend () -> Boolean, onRemovedByUser: suspend () -> Unit): Boolean? {
        if (!isSupported()) return null
        return withNotificationManager("update") { manager ->
            if (!isWanted() || !manager.isNotificationPolicyAccessGranted) {
                // Removals only disable the sensor while it is enabled, and Android removes the app's modes when Do
                // Not Disturb access is lost. Forget the id so neither a removal while the sensor is disabled nor a
                // lost access is later mistaken for the user removing the mode: it is found again by its condition
                // id, or added back.
                storeModeId(null)
                return@withNotificationManager null
            }
            when (val lookup = manager.findMode()) {
                is Lookup.Found -> {
                    storeModeId(lookup.id)
                    manager.isModeActive(lookup.id)
                }
                Lookup.RemovedByUser -> {
                    // Forgotten only once the sensor is disabled, so a failure to disable it is retried
                    onRemovedByUser()
                    storeModeId(null)
                    null
                }
                Lookup.NotAdded -> manager.addMode()?.let { id ->
                    storeModeId(id)
                    manager.isModeActive(id)
                }
            }
        }
    }

    /**
     * Turns the mode on or off, adding it first to turn it on, even after the user removed it: the command is as much
     * the user's choice as the removal.
     *
     * The change applies like tapping the mode on the device, whatever the user did by hand before: the command is the
     * user's own instruction relayed by their Home Assistant, so it is sent as [Condition.SOURCE_USER_ACTION], for which
     * Android drops the user's manual override instead of keeping it. An automatic change would silently do nothing
     * while such an override is active, which nobody can see. Requires Do Not Disturb access, see [hasAccess].
     */
    internal suspend fun setActive(active: Boolean): ChangeResult {
        if (!isSupported()) return ChangeResult.Failed
        return withNotificationManager(if (active) "turn on" else "turn off") { manager ->
            val mode = manager.findMode() as? Lookup.Found
            when {
                mode != null && !mode.rule.isEnabled -> ChangeResult.DisabledInAndroid
                mode == null && !active -> ChangeResult.NotAdded
                else -> {
                    val id = mode?.id ?: manager.addMode() ?: return@withNotificationManager ChangeResult.Failed
                    val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
                    manager.setAutomaticZenRuleState(
                        id,
                        Condition(conditionId, CONDITION_SUMMARY, state, Condition.SOURCE_USER_ACTION),
                    )
                    if (manager.isModeActive(id) == active) ChangeResult.Applied else ChangeResult.NotApplied
                }
            }
        } ?: ChangeResult.Failed
    }

    /** Stores the id of the mode the app added, only while its sensor is enabled and tracks the mode. */
    private suspend fun storeModeId(id: String?) {
        if (id != storage.getString(PREF_MODE_ID)) storage.putString(PREF_MODE_ID, id)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun <T : Any> withNotificationManager(
        action: String,
        block: suspend (NotificationManager) -> T?,
    ): T? {
        val manager = notificationManager ?: return null
        return withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    block(manager)
                } catch (e: SecurityException) {
                    Timber.w(e, "Failed to $action the Home Assistant mode, Do Not Disturb access is missing")
                    null
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Failed to $action the Home Assistant mode for conditionId=$conditionId")
                    null
                }
            }
        }
    }

    /** Finds the mode the app added, without changing what is stored about it. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun NotificationManager.findMode(): Lookup {
        val storedId = storage.getString(PREF_MODE_ID)
        return if (storedId != null) {
            getAutomaticZenRule(storedId)?.let { Lookup.Found(storedId, it) } ?: Lookup.RemovedByUser
        } else {
            // Not stored: the sensor was disabled, Do Not Disturb access was lost, or an earlier version added it
            automaticZenRules.entries.firstOrNull { it.value.conditionId == conditionId }
                ?.let { Lookup.Found(it.key, it.value) }
                ?: Lookup.NotAdded
        }
    }

    /** @return whether the mode is on, or `null` when Android cannot tell, for example because it was just removed */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun NotificationManager.isModeActive(id: String): Boolean? = when (getAutomaticZenRuleState(id)) {
        Condition.STATE_TRUE -> true
        Condition.STATE_FALSE -> false
        else -> null
    }

    /**
     * Adds the mode. Android links to an activity from the mode's page: prefers the app's screen for the mode, declared
     * with [NotificationManager.ACTION_AUTOMATIC_ZEN_RULE], over the launcher activity.
     *
     * While its sensor tracks a mode the user removed, the new mode replaces it, so the sensor is not disabled for the
     * removal on its next update.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun NotificationManager.addMode(): String? {
        val packageManager = context.packageManager
        val configurationActivity = packageManager.resolveActivity(
            Intent(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE).setPackage(context.packageName),
            PackageManager.ResolveInfoFlags.of(0),
        )?.activityInfo?.let { ComponentName(it.packageName, it.name) }
            ?: packageManager.getLaunchIntentForPackage(context.packageName)?.component
        if (configurationActivity == null) {
            Timber.w("Cannot add the Home Assistant mode without an activity to link to")
            return null
        }
        val rule = AutomaticZenRule.Builder(context.getString(commonR.string.app_name), conditionId)
            .setConfigurationActivity(configurationActivity)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setManualInvocationAllowed(true)
            .build()
        return addAutomaticZenRule(rule)?.also { id ->
            if (storage.getString(PREF_MODE_ID) != null) storeModeId(id)
        }
    }
}
