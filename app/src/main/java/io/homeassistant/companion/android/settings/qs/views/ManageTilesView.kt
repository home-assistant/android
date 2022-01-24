package io.homeassistant.companion.android.settings.qs.views

import android.graphics.PorterDuff
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentManager
import com.maltaisn.icondialog.IconDialog
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel

private lateinit var iconPack: IconPack

@Composable
fun ManageTilesView(
    viewModel: ManageTilesViewModel,
    iconDialog: IconDialog,
    childFragment: FragmentManager
) {
    val context = LocalContext.current
    var expandedTile by remember { mutableStateOf(false) }
    var expandedEntity by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(20.dp)) {
        TextButton(onClick = { expandedTile = true }) {
            Text(
                text = stringResource(R.string.tile_select) + viewModel.selectedTile.value,
                fontSize = 15.sp
            )
        }
        DropdownMenu(expanded = expandedTile, onDismissRequest = { expandedTile = false }) {
            val tileNameArray = stringArrayResource(id = io.homeassistant.companion.android.R.array.tile_name)
            val tileIdArray = stringArrayResource(id = io.homeassistant.companion.android.R.array.tile_ids)
            for ((tileName, tileId) in tileNameArray.zip(tileIdArray)) {
                DropdownMenuItem(onClick = {
                    viewModel.selectedTile.value = tileId
                    expandedTile = false
                    if (viewModel.currentTile() != null) {
                        viewModel.tileLabel.value = viewModel.currentTile()!!.label
                        viewModel.tileSubtitle.value = viewModel.currentTile()!!.subtitle
                        viewModel.selectedEntityId.value = viewModel.currentTile()!!.entityId
                        viewModel.selectedIcon.value = viewModel.currentTile()!!.iconId
                        val loader = IconPackLoader(context)
                        iconPack = createMaterialDesignIconPack(loader)
                        iconPack.loadDrawables(loader.drawableLoader)
                        val iconDrawable = viewModel.selectedIcon.value?.let { iconPack.getIcon(it)?.drawable }
                        if (iconDrawable != null) {
                            val icon = DrawableCompat.wrap(iconDrawable)
                            icon.setColorFilter(context.resources.getColor(io.homeassistant.companion.android.R.color.colorAccent), PorterDuff.Mode.SRC_IN)
                            viewModel.icon.value = icon
                        }
                    }
                }) {
                    Text(tileName)
                }
            }
        }
        Text(
            text = stringResource(id = R.string.tile_label),
            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
        )
        TextField(
            value = viewModel.tileLabel.value.toString(),
            onValueChange = { viewModel.tileLabel.value = it },
            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
        )
        Text(
            text = stringResource(id = R.string.tile_subtitle),
            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
        )
        TextField(
            value = viewModel.tileSubtitle.value.toString(),
            onValueChange = { viewModel.tileSubtitle.value = it },
            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)
        )

        TextButton(onClick = {
            iconDialog.show(childFragment, viewModel.selectedTile.value)
        }) {
            Text(
                text = stringResource(id = R.string.tile_icon),
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            viewModel.icon.value?.toBitmap()?.asImageBitmap()
                ?.let { Image(it, contentDescription = stringResource(id = R.string.tile_icon)) }
        }
        TextButton(onClick = { expandedEntity = true }) {
            Text(
                text = stringResource(id = R.string.tile_entity) + ": ${viewModel.selectedEntityId.value}",
                fontSize = 15.sp
            )
        }
        DropdownMenu(expanded = expandedEntity, onDismissRequest = { expandedEntity = false }) {
            val sortedEntities = viewModel.entities.values.sortedBy { it.entityId }
            for (item in sortedEntities) {
                DropdownMenuItem(onClick = {
                    viewModel.selectedEntityId.value = item.entityId
                    expandedEntity = false
                }) {
                    Text(text = item.entityId, fontSize = 15.sp)
                }
            }
        }
        TextButton(onClick = {
            val tileData = TileEntity(
                if (viewModel.currentTile() == null) 0 else viewModel.currentTile()!!.id,
                viewModel.selectedTile.value,
                viewModel.selectedIcon.value,
                viewModel.selectedEntityId.value.toString(),
                viewModel.tileLabel.value.toString(),
                viewModel.tileSubtitle.value
            )
            AppDatabase.getInstance(context).tileDao().add(tileData)
            Toast.makeText(context, R.string.tile_updated, Toast.LENGTH_SHORT).show()
        }) {
            Text(stringResource(id = R.string.tile_save))
        }
    }
}
