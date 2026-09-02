package io.homeassistant.companion.android.util.icondialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Display a grid of icons, letting the user select one.
 * @param icons List of icons to display
 * @param onClick Invoked when the user clicks on the given icon
 */
@Composable
fun IconDialogGrid(
    icons: List<MdiIcon>,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colors.onSurface,
    onClick: (MdiIcon) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 48.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(icons) { icon ->
            IconButton(onClick = { onClick(icon) }) {
                Image(
                    imageVector = icon.rememberImageVector(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(tint),
                    // https://material.io/design/iconography/system-icons.html#color
                    alpha = 0.54f,
                )
            }
        }
    }
}

/**
 * Display a grid of icons from the MDI catalog, letting the user select one.
 * @param searchQuery Search term used to filter the icons.
 * @param iconFilter Adjust filtering logic for the search process.
 * @param onClick Invoked when the user clicks on the given icon
 */
@Composable
fun IconDialogGrid(
    searchQuery: String,
    modifier: Modifier = Modifier,
    iconFilter: IconFilter = DefaultIconFilter(),
    tint: Color = MaterialTheme.colors.onSurface,
    onClick: (MdiIcon) -> Unit,
) {
    var icons by remember { mutableStateOf<List<MdiIcon>>(emptyList()) }
    LaunchedEffect(searchQuery) {
        icons = withContext(Dispatchers.Default) { iconFilter.queryIcons(searchQuery) }
    }

    IconDialogGrid(
        icons = icons,
        modifier = modifier,
        tint = tint,
        onClick = onClick,
    )
}

@Preview
@Composable
private fun IconDialogGridPreview() {
    HomeAssistantAppTheme {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            IconDialogGrid(
                icons = Mdi.icons.toList(),
                onClick = {},
            )
        }
    }
}
