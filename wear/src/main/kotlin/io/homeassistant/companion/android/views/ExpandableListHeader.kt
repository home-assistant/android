package io.homeassistant.companion.android.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.Text
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ChevronDown
import io.github.timoptr.mdiicons.generated.ChevronUp
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR

/**
 * Remember expanded state of each header
 */
@Composable
fun <K> rememberExpandedStates(initialKeys: Iterable<K>): SnapshotStateMap<K, Boolean> {
    val defaultExpanded = if (initialKeys is Collection) {
        initialKeys.size == 1
    } else {
        false
    }
    return remember {
        mutableStateMapOf<K, Boolean>().apply {
            initialKeys.forEach { key ->
                put(key, defaultExpanded)
            }
        }
    }
}

@Composable
fun ExpandableListHeader(string: String, expanded: Boolean, onExpandChanged: (Boolean) -> Unit) {
    androidx.wear.compose.material.ListHeader(
        modifier = Modifier
            .clickable { onExpandChanged(!expanded) },
    ) {
        Row {
            Text(
                text = string,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Image(
                imageVector = if (expanded) {
                    Mdi.ChevronUp
                } else {
                    Mdi.ChevronDown
                }.rememberImageVector(),
                contentDescription = stringResource(if (expanded) commonR.string.collapse else commonR.string.expand),
                colorFilter = ColorFilter.tint(LocalContentColor.current),
            )
        }
    }
}

@Composable
fun <K> ExpandableListHeader(string: String, key: K, expandedStates: SnapshotStateMap<K, Boolean>) {
    ExpandableListHeader(
        string = string,
        expanded = expandedStates.getOrDefault(key, true),
        onExpandChanged = { expandedStates[key] = it },
    )
}

@Preview
@Composable
private fun PreviewExpandableListHeader() {
    ExpandableListHeader(
        string = stringResource(commonR.string.other),
        expanded = true,
        onExpandChanged = {},
    )
}
