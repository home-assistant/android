package io.homeassistant.companion.android.settings.assist

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.isAutomotive
import javax.inject.Inject

/**
 * Manages default assistant status and provides intents for setting the app as default.
 */
interface DefaultAssistantManager {
    /**
     * Returns true if this app is currently the default assistant.
     */
    fun isDefaultAssistant(): Boolean

    /**
     * Checks if we should suggest the user to set up the assistant.
     *
     * Returns true if:
     * - At least one server has version 2023.5+
     * - Device is not automotive
     * - On Android Q+: RoleManager assistant role is available but not held by this app
     * - On older versions: This app is not the current default assistant
     */
    suspend fun shouldSuggestAssistantSetup(): Boolean

    /**
     * Returns an intent to open the system settings for changing the default assistant.
     *
     * Tries the specific ManageAssistActivity first (more direct), falls back to
     * default apps settings if not available on this device.
     */
    fun getSetDefaultAssistantIntent(): Intent
}

class DefaultAssistantManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
) : DefaultAssistantManager {

    override fun isDefaultAssistant(): Boolean {
        return AssistVoiceInteractionService.isActiveService(context)
    }

    override suspend fun shouldSuggestAssistantSetup(): Boolean {
        // Require at least one server with version 2023.5+
        val hasCompatibleServer = serverManager.servers().any { it.version?.isAtLeast(2023, 5) == true }
        if (!hasCompatibleServer) return false

        // Not supported on automotive
        if (context.isAutomotive()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true &&
                !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        } else {
            val defaultApp: String? = Settings.Secure.getString(context.contentResolver, "assistant")
            defaultApp?.contains(BuildConfig.APPLICATION_ID) == false
        }
    }

    override fun getSetDefaultAssistantIntent(): Intent {
        // Try the specific ManageAssistActivity first - more direct path to change assistant
        val specificIntent = Intent("android.settings.VOICE_INPUT_SETTINGS").apply {
            component = ComponentName(
                "com.android.settings",
                $$"com.android.settings.Settings$ManageAssistActivity",
            )
        }

        // Check if this specific activity exists on this device
        val resolveInfo = context.packageManager.resolveActivity(
            specificIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )

        return if (resolveInfo != null) {
            specificIntent
        } else {
            // Fall back to default apps settings (requires one more tap)
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
    }
}
