package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.items
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.playPreviewEntityScene1
import io.homeassistant.companion.android.util.playPreviewEntityScene2
import io.homeassistant.companion.android.util.playPreviewEntityScene3
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.views.ExpandableListHeader
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.views.rememberExpandedStates
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun EntityViewList(
    entityLists: Map<String, List<Entity<*>>>,
    entityListsOrder: List<String>,
    entityListFilter: (Entity<*>) -> Boolean,
    onEntityClicked: (String, String) -> Unit,
    onEntityLongClicked: (String) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean
) {
    // Remember expanded state of each header
    val expandedStates = rememberExpandedStates(entityLists.keys.map { it.hashCode() })

    WearAppTheme {
        ThemeLazyColumn {
            for (header in entityListsOrder) {
                val entities = entityLists[header].orEmpty()
                if (entities.isNotEmpty()) {
                    item {
                        if (entityLists.size > 1) {
                            ExpandableListHeader(
                                string = header,
                                key = header.hashCode(),
                                expandedStates = expandedStates
                            )
                        } else {
                            ListHeader(header)
                        }
                    }
                    if (expandedStates[header.hashCode()]!!) {
                        val filtered = entities.filter { entityListFilter(it) }
                        items(filtered, key = { it.entityId }) { entity ->
                            EntityUi(
                                entity,
                                onEntityClicked,
                                isHapticEnabled,
                                isToastEnabled
                            ) { entityId -> onEntityLongClicked(entityId) }
                        }

                        if (filtered.isEmpty()) {
                            item {
                                Column {
                                    Chip(
                                        label = {
                                            Text(
                                                text = stringResource(commonR.string.loading_entities),
                                                textAlign = TextAlign.Center
                                            )
                                        },
                                        onClick = { /* No op */ },
                                        colors = ChipDefaults.primaryChipColors()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewEntityListView() {
    EntityViewList(
        entityLists = mapOf(stringResource(commonR.string.lights) to listOf(previewEntity1, previewEntity2)),
        entityListsOrder = listOf(stringResource(commonR.string.lights)),
        entityListFilter = { true },
        onEntityClicked = { _, _ -> },
        onEntityLongClicked = { _ -> },
        isHapticEnabled = false,
        isToastEnabled = false
    )
}

@Preview
@Composable
private fun PreviewEntityListScenes() {
    EntityViewList(
        entityLists = mapOf(stringResource(commonR.string.scenes) to listOf(playPreviewEntityScene1, playPreviewEntityScene2, playPreviewEntityScene3)),
        entityListsOrder = listOf(stringResource(commonR.string.scenes)),
        entityListFilter = { true },
        onEntityClicked = { _, _ -> },
        onEntityLongClicked = { _ -> },
        isHapticEnabled = false,
        isToastEnabled = false
    )
}
