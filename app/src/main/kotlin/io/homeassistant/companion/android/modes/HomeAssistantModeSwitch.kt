package io.homeassistant.companion.android.modes

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SAVED_ADD = "add"
private const val SAVED_REMOVE = "remove"

/**
 * What the switch of the Home Assistant mode in the app's settings shows and does, apart from the screen.
 *
 * Changing the switch without Do Not Disturb access sends the user to grant it. The change is applied once they come
 * back with access and the app is unlocked, however they return: with back, or by switching apps, which gives no
 * activity result. One instance belongs to one settings screen, which saves [savedState] with its own state.
 *
 * The screen calls [onSwitchChanged], [onResume] and [onUnlocked] from separate coroutines. Each of them waits for the
 * one before it to end, so what the screen shows last is what was read last, and a change requested meanwhile is not
 * lost.
 */
internal class HomeAssistantModeSwitch(private val homeAssistantMode: HomeAssistantMode) {

    /**
     * What the switch shows.
     *
     * @property checked whether the switch is on: the mode is added, never while access is missing since the app
     * cannot tell then
     * @property summary the text under the switch, which says when access is missing
     */
    data class Display(val checked: Boolean, @param:StringRes val summary: Int)

    /** Something the screen must do besides showing [Update.display]. */
    sealed interface Action {
        /** Open Android's Do Not Disturb access settings, then call [onAccessScreenUnavailable] if there is none. */
        data object RequestAccess : Action

        /** The mode was added or removed: report it to the mode's sensor right away. */
        data object ReportChange : Action

        /** Tell the user why their change was not made. */
        data class ShowMessage(@param:StringRes val message: Int) : Action
    }

    /**
     * What the screen does after an event.
     *
     * @property display what the switch shows, `null` to leave it as it is
     * @property action what the screen must do besides, if anything
     */
    data class Update(val display: Display?, val action: Action? = null)

    /** The change waiting for the user to grant access: `true` to add the mode, `false` to remove it. */
    private var pendingChange: Boolean? = null

    /** Whether the settings came back to the foreground since [pendingChange] was requested, see [onUnlocked]. */
    private var returnedSinceRequest = false

    /** Lets one of [onSwitchChanged], [onResume] and [onUnlocked] run at a time. */
    private val mutex = Mutex()

    /** The change waiting for access, as saved with the settings screen's state, see [restore]. */
    val savedState: String?
        get() = pendingChange?.let { add -> if (add) SAVED_ADD else SAVED_REMOVE }

    /** Restores the change waiting for access from [savedState] after the settings screen was recreated. */
    fun restore(savedState: String?) {
        pendingChange = when (savedState) {
            SAVED_ADD -> true
            SAVED_REMOVE -> false
            else -> null
        }
        returnedSinceRequest = false
    }

    /** Adds the mode when [add] is `true`, removes it otherwise, after the user grants access if it is missing. */
    suspend fun onSwitchChanged(add: Boolean): Update = mutex.withLock {
        if (homeAssistantMode.hasAccess()) {
            change(add)
        } else {
            pendingChange = add
            returnedSinceRequest = false
            Update(display = null, action = Action.RequestAccess)
        }
    }

    /** Drops the change waiting for access when the device has no screen to grant it. */
    fun onAccessScreenUnavailable(): Update {
        pendingChange = null
        return Update(
            display = null,
            action = Action.ShowMessage(commonR.string.home_assistant_mode_access_unavailable),
        )
    }

    /**
     * Shows the current state when the settings come back to the foreground: the user can remove the mode or revoke
     * access in Android's settings meanwhile. A change waiting for access is kept for [onUnlocked], since the app may
     * still ask the user to unlock it.
     */
    suspend fun onResume(): Update = mutex.withLock {
        if (pendingChange != null) returnedSinceRequest = true
        Update(display())
    }

    /**
     * Applies the change waiting for access once the settings are back in the foreground and unlocked, if the user
     * granted access; drops it otherwise, so it is never applied later by surprise. Does nothing before the user left
     * for Android's access screen and came back, see [onResume].
     *
     * Leaving the settings cancels this. A change that was not made yet is then made when the user comes back.
     */
    suspend fun onUnlocked(): Update? = mutex.withLock {
        pendingChange?.takeIf { returnedSinceRequest }?.let { add ->
            val update = if (homeAssistantMode.hasAccess()) change(add) else Update(display())
            // The change is kept until it is made or dropped: the screen can be left or recreated meanwhile, and
            // restores it then. Adding or removing the mode a second time changes nothing.
            pendingChange = null
            returnedSinceRequest = false
            update
        }
    }

    private suspend fun change(add: Boolean): Update {
        val changed = if (add) homeAssistantMode.add() else homeAssistantMode.remove()
        val display = display()
        return if (changed) {
            Update(display, Action.ReportChange)
        } else {
            val message = if (add) {
                commonR.string.home_assistant_mode_add_failed
            } else {
                commonR.string.home_assistant_mode_remove_failed
            }
            Update(display, Action.ShowMessage(message))
        }
    }

    private suspend fun display(): Display {
        // Null without access, which is checked only then
        val state = homeAssistantMode.getState()
        val hasAccess = state != null || homeAssistantMode.hasAccess()
        return Display(
            checked = state?.added == true,
            summary = if (hasAccess) {
                commonR.string.home_assistant_mode_summary
            } else {
                commonR.string.home_assistant_mode_summary_no_access
            },
        )
    }
}
