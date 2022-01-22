package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Text
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalComposeUiApi
@Composable
fun EntityViewList(
    entityLists: Map<String, List<Entity<*>>>,
    entityListsOrder: List<String>,
    entityListFilter: (Entity<*>) -> Boolean,
    onEntityClicked: (String, String) -> Unit,
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
                        items(filtered.size) { index ->
                            EntityUi(
                                filtered[index],
                                onEntityClicked,
                                isHapticEnabled,
                                isToastEnabled
                            )
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

@ExperimentalComposeUiApi
@ExperimentalWearMaterialApi
@Preview
@Composable
private fun PreviewEntityListView() {
    EntityViewList(
        entityLists = mapOf(stringResource(commonR.string.lights) to listOf(previewEntity1, previewEntity2)),
        entityListsOrder = listOf(stringResource(commonR.string.lights)),
        entityListFilter = { true },
        onEntityClicked = { _, _ -> },
        isHapticEnabled = false,
        isToastEnabled = false
    )
}
