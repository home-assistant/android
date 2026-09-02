package io.homeassistant.companion.android.settings.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Build
import android.util.NoSuchPropertyException
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MDI_PREFIX
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.database.IconDialogCompat
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.link.HA_DEEP_LINK_SCHEME
import io.homeassistant.companion.android.launch.link.LinkActivity
import io.homeassistant.companion.android.launch.link.navigateDeepLinkUri
import io.homeassistant.companion.android.widgets.assist.AssistShortcutActivity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Key used by WebViewActivity to store the server used in the shortcut within the Intent.
 * This value cannot be changed, otherwise it's going to break shortcuts.
 */
internal const val SHORTCUT_EXTRA_SERVER = "server"

/**
 * Key used by WebViewActivity to store the path used in the shortcut within the Intent.
 * This value cannot be changed, otherwise it's going to break shortcuts.
 */
internal const val SHORTCUT_EXTRA_PATH = "path"
private const val SHORTCUT_EXTRA_ICON_NAME = "iconName"
private const val SHORTCUT_EXTRA_ICON_ID = "iconId"

/**
 * Manage Shortcuts.
 *
 * Shortcuts launch through the stable, exported [LinkActivity] via a `homeassistant://navigate`
 * deep link. Older app versions launched `WebViewActivity` directly; [migrateLegacyShortcuts]
 * rewrites those to the current format so they keep working once `WebViewActivity` is removed.
 */
@Singleton
internal class HaShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iconDialogCompat: IconDialogCompat,
) {

    /**
     * Builds a [ShortcutInfoCompat] that launches [path] on [serverId] through a
     * `homeassistant://navigate` deep link.
     *
     * The launch destination lives in the intent data URI; `ShortcutInfo` serializes only the extras
     * into a [android.os.PersistableBundle], so the server id and raw path are kept as primitive
     * extras (used to repopulate the edit form).
     */
    fun buildShortcutInfo(
        shortcutId: String,
        serverId: Int,
        label: String,
        longLabel: String,
        path: String,
        icon: MdiIcon?,
    ): ShortcutInfoCompat {
        val intent = Intent(
            Intent.ACTION_VIEW,
            navigateDeepLinkUri(FrontendTarget.fromRawPath(path), serverId),
        ).apply {
            // Scope to our app rather than hard-coding the activity: the persisted shortcut then
            // resolves to whichever activity handles homeassistant://navigate (currently
            // LinkActivity), so it survives internal navigation refactors, while the package scope
            // still prevents another app registering the scheme from intercepting it.
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(SHORTCUT_EXTRA_SERVER, serverId)
            putExtra(SHORTCUT_EXTRA_PATH, path)
            icon?.let { putExtra(SHORTCUT_EXTRA_ICON_NAME, "$MDI_PREFIX${it.name}") }
        }
        return ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(
                // Fall back to the launcher icon (an AdaptiveIcon) so it gets themed by the system.
                icon?.toAdaptiveIcon() ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher),
            )
            .setIntent(intent)
            .build()
    }

    /**
     * Resolves the [MdiIcon] stored on a shortcut intent via its `iconName`/`iconId` extras, or
     * `null` if none is present.
     */
    suspend fun resolveIconFromIntent(intent: Intent): MdiIcon? = withContext(Dispatchers.IO) {
        val extras = intent.extras ?: return@withContext null
        return@withContext when {
            extras.containsKey(SHORTCUT_EXTRA_ICON_NAME) -> extras.getString(SHORTCUT_EXTRA_ICON_NAME)?.let {
                Mdi.fromHaName(it)
            }

            extras.containsKey(SHORTCUT_EXTRA_ICON_ID) -> {
                extras.getInt(SHORTCUT_EXTRA_ICON_ID).takeIf { it != 0 }?.let { iconId ->
                    try {
                        Mdi.fromHaName("$MDI_PREFIX${iconDialogCompat.streamingIconLookup(iconId)}")
                    } catch (e: NoSuchPropertyException) {
                        Timber.w(e, "Unknown shortcut iconId=$iconId, falling back to default icon")
                        null
                    }
                }
            }

            else -> null
        }
    }

    /**
     * Migrates shortcuts created by older app versions (which launched `WebViewActivity` directly)
     * to the current deep link format, updating dynamic and pinned shortcuts in place while
     * preserving their label, icon and target.
     *
     * Best-effort and idempotent: shortcuts already pointing at the `homeassistant://` deep link are
     * skipped, so re-running does nothing. Safe to call on any API level (no-op below N_MR1).
     */
    suspend fun migrateLegacyShortcuts() {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.N_MR1)) return

        val legacyShortcuts = ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED,
        ).filter {
            !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) &&
                it.intent.data?.scheme != HA_DEEP_LINK_SCHEME &&
                it.intent.hasExtra(SHORTCUT_EXTRA_PATH)
        }
        if (legacyShortcuts.isEmpty()) return
        Timber.d("Shortcut to migrate ${legacyShortcuts.map { it.id }}")

        val migrated = legacyShortcuts.mapNotNull { old ->
            val oldIntent = old.intent
            // The filter above only keeps shortcuts carrying the "path" extra, so it is normally
            // present; bail defensively if it is somehow missing rather than guessing a target.
            val path = oldIntent.getStringExtra(SHORTCUT_EXTRA_PATH) ?: return@mapNotNull null
            buildShortcutInfo(
                shortcutId = old.id,
                serverId = oldIntent.getIntExtra(SHORTCUT_EXTRA_SERVER, ServerManager.SERVER_ID_ACTIVE),
                label = old.shortLabel.toString(),
                longLabel = old.longLabel?.toString().orEmpty(),
                path = path,
                icon = resolveIconFromIntent(oldIntent),
            )
        }

        try {
            ShortcutManagerCompat.updateShortcuts(context, migrated)
            Timber.d("Migrated ${migrated.size} legacy shortcut(s) to deep link")
        } catch (e: IllegalStateException) {
            Timber.e(e, "Failed to migrate legacy shortcuts")
        }
    }

    /**
     * Replicates an AdaptiveIcon from an [MdiIcon] by drawing it centered on a themed
     * launcher-colored background, returned as an [IconCompat] flagged as an AdaptiveIcon.
     */
    private fun MdiIcon.toAdaptiveIcon(): IconCompat {
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            48f,
            context.resources.displayMetrics,
        ).toInt()
        val iconBitmap = toBitmap(iconSize, ContextCompat.getColor(context, commonR.color.colorAccent))

        val adaptiveIconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            108f,
            context.resources.displayMetrics,
        ).toInt()
        val adaptiveBitmap = createBitmap(adaptiveIconSize, adaptiveIconSize)
        val canvas = Canvas(adaptiveBitmap)
        // Use the same color as the foreground of the launcher as background
        canvas.drawColor(ContextCompat.getColor(context, R.color.ic_launcher_foreground))
        val x = (canvas.width - iconBitmap.width) / 2f
        val y = (canvas.height - iconBitmap.height) / 2f
        canvas.drawBitmap(iconBitmap, x, y, null)

        return IconCompat.createWithAdaptiveBitmap(adaptiveBitmap)
    }
}
