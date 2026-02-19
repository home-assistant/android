package io.homeassistant.companion.android.settings.shortcuts.v2.di

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutFactory
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.settings.shortcuts.v2.util.ShortcutIconRenderer
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

internal class WebViewShortcutFactory @Inject constructor(
    @ApplicationContext private val app: Context,
    private val shortcutIntentCodec: ShortcutIntentCodec,
) : ShortcutFactory {
    override fun createShortcutInfo(draft: ShortcutDraft): ShortcutInfoCompat {
        val encodedPath = shortcutIntentCodec.encodeTarget(draft.target)
        val intent = WebViewActivity.newInstance(app, encodedPath, draft.serverId).apply {
            action = Intent.ACTION_VIEW
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
            shortcutIntentCodec.applyShortcutExtras(
                intent = this,
                target = draft.target,
                path = encodedPath,
                iconName = draft.selectedIconName,
            )
        }

        val builder = ShortcutInfoCompat.Builder(app, draft.id)
            .setShortLabel(draft.label)
            .setIcon(
                draft.selectedIconName
                    ?.let(CommunityMaterial::getIconByMdiName)
                    ?.let { ShortcutIconRenderer.renderAdaptiveIcon(app, it) }
                    ?: IconCompat.createWithResource(app, R.mipmap.ic_launcher),
            )
            .setIntent(intent)

        draft.description.takeIf { it.isNotBlank() }?.let(builder::setLongLabel)

        return builder.build()
    }
}
