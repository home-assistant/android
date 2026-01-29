package io.homeassistant.companion.android.common.data.shortcuts.impl

import android.os.Bundle
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentCodec
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutIntentKeys
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ShortcutIntentCodecImpl @Inject constructor() : ShortcutIntentCodec {
    override fun parseIcon(extras: Bundle?, iconIdToName: Map<Int, String>): String? {
        val bundle = extras ?: return null

        return when {
            bundle.containsKey(ShortcutIntentKeys.EXTRA_ICON_NAME) -> {
                val iconName = bundle.getString(ShortcutIntentKeys.EXTRA_ICON_NAME) ?: return null
                if (iconName.startsWith(MDI_PREFIX)) iconName else "$MDI_PREFIX$iconName"
            }

            bundle.containsKey(ShortcutIntentKeys.EXTRA_ICON_ID) -> {
                val iconId = bundle.getInt(ShortcutIntentKeys.EXTRA_ICON_ID)
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

    private fun parseShortcutType(extras: Bundle?, path: String): ShortcutType {
        val raw = extras?.getString(ShortcutIntentKeys.EXTRA_TYPE)
        val fromExtra = raw?.let { ShortcutType.entries.firstOrNull { e -> e.name == it } }
        return fromExtra ?: if (path.startsWith(ENTITY_PREFIX)) ShortcutType.ENTITY_ID else ShortcutType.LOVELACE
    }

    private companion object {
        private const val ENTITY_PREFIX = "entityId:"
        private const val MDI_PREFIX = "mdi:"
    }
}
