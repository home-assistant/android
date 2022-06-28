package io.homeassistant.companion.android.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Text
import io.homeassistant.companion.android.common.R
import org.checkerframework.checker.units.qual.K

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
                put(key, true)
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
            val plusMinus = if (expanded) "-" else "+"
            Text(
                text = "$string\u2001$plusMinus"
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
        string = stringResource(R.string.other),
        expanded = true,
        onExpandChanged = {}
    )
}
