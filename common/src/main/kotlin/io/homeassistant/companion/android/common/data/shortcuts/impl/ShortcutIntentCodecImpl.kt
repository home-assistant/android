package io.homeassistant.companion.android.common.data.shortcuts.impl

import android.content.Intent
import android.os.Bundle
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toShortcutType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ShortcutIntentCodecImpl @Inject constructor() : ShortcutIntentCodec {
    override fun parseIcon(extras: Bundle?, iconIdToName: Map<Int, String>): String? {
        val bundle = extras ?: return null

        return when {
            bundle.containsKey(EXTRA_ICON_NAME) -> {
                val iconName = bundle.getString(EXTRA_ICON_NAME) ?: return null
                if (iconName.startsWith(MDI_PREFIX)) iconName else "$MDI_PREFIX$iconName"
            }

            bundle.containsKey(EXTRA_ICON_ID) -> {
                val iconId = bundle.getInt(EXTRA_ICON_ID)
                if (iconId == 0) return null
                val iconName = iconIdToName[iconId] ?: return null
                if (iconName.startsWith(MDI_PREFIX)) iconName else "$MDI_PREFIX$iconName"
            }

            else -> null
        }
    }

    override fun parseTarget(extras: Bundle?, path: String): ShortcutTargetValue {
        return when (parseShortcutType(extras, path)) {
            ShortcutType.LOVELACE -> {
                ShortcutTargetValue.Lovelace(path.takeUnless { it.startsWith(ENTITY_PREFIX) }.orEmpty())
            }

            ShortcutType.ENTITY_ID -> ShortcutTargetValue.Entity(path.removePrefix(ENTITY_PREFIX))
        }
    }

    override fun encodeTarget(target: ShortcutTargetValue): String {
        return when (target) {
            is ShortcutTargetValue.Lovelace -> target.path
            is ShortcutTargetValue.Entity -> "$ENTITY_PREFIX${target.entityId}"
        }
    }

    override fun applyShortcutExtras(intent: Intent, target: ShortcutTargetValue, path: String, iconName: String?) {
        intent.putExtra(EXTRA_SHORTCUT_PATH, path)
        iconName?.let { intent.putExtra(EXTRA_ICON_NAME, it) }
        intent.putExtra(EXTRA_TYPE, target.toShortcutType().name)
    }

    private fun parseShortcutType(extras: Bundle?, path: String): ShortcutType {
        val raw = extras?.getString(EXTRA_TYPE)
        val fromExtra = raw?.let { ShortcutType.entries.firstOrNull { e -> e.name == it } }
        return fromExtra ?: if (path.startsWith(ENTITY_PREFIX)) ShortcutType.ENTITY_ID else ShortcutType.LOVELACE
    }

    internal companion object {
        private const val ENTITY_PREFIX = "entityId:"
        private const val MDI_PREFIX = "mdi:"
        private const val EXTRA_ICON_ID = "iconId"
        const val EXTRA_ICON_NAME = "iconName"
        const val EXTRA_SHORTCUT_PATH = "shortcutPath"
        const val EXTRA_TYPE = "type"
    }
}
