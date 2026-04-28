package io.homeassistant.companion.android.util.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import sh.calvin.reorderable.ReorderableCollectionItemScope

@Composable
fun ReorderableCollectionItemScope.FavoriteEntityRow(
    entityName: String,
    entityId: String,
    onClick: () -> Unit,
    checked: Boolean,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    isDragging: Boolean = false,
) {
    val toneElevation = animateDpAsState(targetValue = if (isDragging) 8.dp else 0.dp)
    var rowModifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    if (draggable) {
        rowModifier = rowModifier.longPressDraggableHandle()
    }
    Surface(
        tonalElevation = toneElevation.value,
        modifier = modifier,
        color = LocalHAColorScheme.current.colorSurfaceDefault,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            ) {
                Text(
                    text = entityName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalHAColorScheme.current.colorTextPrimary,
                )
                Text(
                    text = entityId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalHAColorScheme.current.colorOnNeutralQuiet,
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = if (checked) Icons.Default.Clear else Icons.Default.Add,
                    contentDescription = stringResource(if (checked) R.string.delete else R.string.add_favorite),
                    tint = LocalHAColorScheme.current.colorTextPrimary,
                )
            }
            if (draggable) {
                Image(
                    asset = CommunityMaterial.Icon.cmd_drag_horizontal_variant,
                    contentDescription = stringResource(R.string.hold_to_reorder),
                    colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorOnNeutralQuiet),
                    modifier = Modifier
                        .size(width = 40.dp, height = 24.dp)
                        .padding(end = 16.dp),
                )
            }
        }
    }
}
