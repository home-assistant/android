package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun EntityViewList(
    entityLists: Map<Int, List<Entity<*>>>,
    onEntityClicked: (String, String) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean
) {
    // Remember expanded state of each header
    val expandedStates = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            entityLists.forEach {
                put(it.key, true)
            }
        }
    }

    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)

    WearAppTheme {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 24.dp,
                start = 8.dp,
                end = 8.dp,
                bottom = 48.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            state = scalingLazyListState
        ) {
            for ((headerId, entities) in entityLists) {
                if (entities.isNotEmpty()) {
                    item {
                        if (entityLists.size > 1) {
                            ListHeader(
                                stringId = headerId,
                                expanded = expandedStates[headerId]!!,
                                onExpandChanged = { expandedStates[headerId] = it }
                            )
                        } else {
                            ListHeader(headerId)
                        }
                    }
                    if (expandedStates[headerId]!!) {
                        items(entities.size) { index ->
                            EntityUi(
                                entities[index],
                                onEntityClicked,
                                isHapticEnabled,
                                isToastEnabled
                            )
                        }

                        if (entities.isNullOrEmpty()) {
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

@ExperimentalWearMaterialApi
@Preview
@Composable
private fun PreviewEntityListView() {
    val rotaryEventDispatcher = RotaryEventDispatcher()

    CompositionLocalProvider(
        LocalRotaryEventDispatcher provides rotaryEventDispatcher
    ) {
        EntityViewList(
            entityLists = mapOf(commonR.string.lights to listOf(previewEntity1, previewEntity2)),
            onEntityClicked = { _, _ -> },
            isHapticEnabled = false,
            isToastEnabled = false
        )
    }
}
