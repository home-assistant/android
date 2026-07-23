package io.homeassistant.companion.android.util.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import sh.calvin.reorderable.ReorderableCollectionItemScope

/**
 * A row displaying a favorite entity using the same display model as the entity picker:
 * resolved icon and name with the area/device context as subtitle, falling back to the
 * entity id when no metadata is available.
 */
@Composable
fun ReorderableCollectionItemScope.FavoriteEntityRow(
    entity: EntityDisplayItem,
    onClick: () -> Unit,
    checked: Boolean,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    isDragging: Boolean = false,
) {
    val surfaceElevation = animateDpAsState(targetValue = if (isDragging) 8.dp else 0.dp)
    var rowModifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    if (draggable) {
        rowModifier = rowModifier.longPressDraggableHandle()
    }
    Surface(
        elevation = surfaceElevation.value,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier,
        ) {
            Image(
                asset = entity.icon,
                colorFilter = ColorFilter.tint(LocalContentColor.current),
                contentDescription = null,
                modifier = Modifier.padding(start = 16.dp).size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            ) {
                Text(text = entity.name, style = MaterialTheme.typography.body1)
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        text = entity.subtitle(LocalLayoutDirection.current) ?: entity.entityId,
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = if (checked) Icons.Default.Clear else Icons.Default.Add,
                    contentDescription = stringResource(if (checked) R.string.delete else R.string.add_favorite),
                )
            }
            if (draggable) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Image(
                        asset = CommunityMaterial.Icon.cmd_drag_horizontal_variant,
                        contentDescription = stringResource(R.string.hold_to_reorder),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        modifier = Modifier
                            .size(width = 40.dp, height = 24.dp)
                            .padding(end = 16.dp)
                            .alpha(LocalContentAlpha.current),
                    )
                }
            }
        }
    }
}
