package io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue

private const val TARGET_TYPE_LOVELACE = "lovelace"
private const val TARGET_TYPE_ENTITY = "entity"

internal val ShortcutDraftSaver: Saver<ShortcutDraft, Any> = listSaver(
    save = { draft ->
        val targetType = when (draft.target) {
            is ShortcutTargetValue.Lovelace -> TARGET_TYPE_LOVELACE
            is ShortcutTargetValue.Entity -> TARGET_TYPE_ENTITY
        }
        val targetValue = when (val target = draft.target) {
            is ShortcutTargetValue.Lovelace -> target.path
            is ShortcutTargetValue.Entity -> target.entityId
        }
        listOf(
            draft.id,
            draft.serverId,
            draft.selectedIconName,
            draft.label,
            draft.description,
            targetType,
            targetValue,
        )
    },
    restore = { values ->
        val id = values[0] as String
        val serverId = values[1] as Int
        val iconName = values[2] as String?
        val label = values[3] as String
        val description = values[4] as String
        val targetType = values[5] as String
        val targetValue = values[6] as String
        val target = if (targetType == TARGET_TYPE_ENTITY) {
            ShortcutTargetValue.Entity(targetValue)
        } else {
            ShortcutTargetValue.Lovelace(targetValue)
        }
        ShortcutDraft(
            id = id,
            serverId = serverId,
            selectedIconName = iconName,
            label = label,
            description = description,
            target = target,
        )
    },
)
