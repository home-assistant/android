package io.homeassistant.companion.android.settings.shortcuts.v2.di

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentKeys
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toShortcutType
import io.homeassistant.companion.android.settings.shortcuts.v2.util.ShortcutIconRenderer
import io.homeassistant.companion.android.util.icondialog.mdiName
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

class WebViewShortcutFactory @Inject constructor(
    @ApplicationContext private val app: Context,
    private val shortcutIntentCodec: ShortcutIntentCodec,
) : ShortcutFactory {
    override fun createShortcutInfo(draft: ShortcutDraft): ShortcutInfoCompat {
        val encodedPath = shortcutIntentCodec.encodeTarget(draft.target)
        val intent = Intent(
            WebViewActivity.newInstance(app, encodedPath, draft.serverId).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
        intent.action = Intent.ACTION_VIEW
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        intent.putExtra(ShortcutIntentKeys.EXTRA_SHORTCUT_PATH, encodedPath)
        draft.selectedIcon?.let { intent.putExtra(ShortcutIntentKeys.EXTRA_ICON_NAME, draft.selectedIcon?.mdiName) }
        intent.putExtra(ShortcutIntentKeys.EXTRA_TYPE, draft.target.toShortcutType().name)

        return ShortcutInfoCompat.Builder(app, draft.id)
            .setShortLabel(draft.label)
            .setLongLabel(draft.description)
            .setIcon(
                draft.selectedIcon?.let { ShortcutIconRenderer.renderAdaptiveIcon(app, it) }
                    ?: // Use launcher icon that is an AdaptiveIcon so it gets themed properly by the system
                    IconCompat.createWithResource(app, R.mipmap.ic_launcher),
            )
            .setIntent(intent)
            .build()
    }
}
