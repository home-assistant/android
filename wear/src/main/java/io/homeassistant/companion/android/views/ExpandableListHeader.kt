package io.homeassistant.companion.android.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import io.homeassistant.companion.android.R
import org.checkerframework.checker.units.qual.K
import io.homeassistant.companion.android.common.R as commonR

/**
 * Remember expanded state of each header
 */
@Composable
fun <K> rememberExpandedStates(
    initialKeys: Iterable<K>
): SnapshotStateMap<K, Boolean> {
    return remember {
        mutableStateMapOf<K, Boolean>().apply {
            initialKeys.forEach { key ->
                put(key, false)
            }
        }
    }
}

@Composable
fun ExpandableListHeader(
    string: String,
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit
) {
    androidx.wear.compose.material.ListHeader(
        modifier = Modifier
            .clickable { onExpandChanged(!expanded) }
    ) {
        Row {
            Text(
                text = string
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painterResource(if (expanded) R.drawable.ic_collapse else R.drawable.ic_expand),
                contentDescription = stringResource(if (expanded) commonR.string.collapse else commonR.string.expand)
            )
        }
    }
}

@Composable
fun <K> ExpandableListHeader(
    string: String,
    key: K,
    expandedStates: SnapshotStateMap<K, Boolean>
) {
    ExpandableListHeader(
        string = string,
        expanded = expandedStates.getOrDefault(key, true),
        onExpandChanged = { expandedStates[key] = it }
    )
}

@Preview
@Composable
private fun PreviewExpandableListHeader() {
    ExpandableListHeader(
        string = stringResource(commonR.string.other),
        expanded = true,
        onExpandChanged = {}
    )
}
