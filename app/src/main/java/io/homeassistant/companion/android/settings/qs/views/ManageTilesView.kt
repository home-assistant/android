package io.homeassistant.companion.android.settings.qs.views

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentManager
import com.maltaisn.icondialog.IconDialog
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel

@Composable
fun ManageTilesView(
    viewModel: ManageTilesViewModel,
    iconDialog: IconDialog,
    childFragment: FragmentManager
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var expandedTile by remember { mutableStateOf(false) }
    var expandedEntity by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.tile_select),
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            Box {
                OutlinedButton(onClick = { expandedTile = true }) {
                    Text(
                        viewModel.selectedTileName.value
                    )
                }

                DropdownMenu(expanded = expandedTile, onDismissRequest = { expandedTile = false }) {
                    val tileNameArray =
                        stringArrayResource(id = io.homeassistant.companion.android.R.array.tile_name)
                    val tileIdArray =
                        stringArrayResource(id = io.homeassistant.companion.android.R.array.tile_ids)
                    for ((tileName, tileId) in tileNameArray.zip(tileIdArray)) {
                        DropdownMenuItem(onClick = {
                            viewModel.selectedTile.value = tileId
                            viewModel.selectedTileName.value = tileName
                            expandedTile = false
                            if (viewModel.currentTile() != null)
                                viewModel.updateExistingTileFields()
                        }) {
                            Text(tileName)
                        }
                    }
                }
            }
        }

        Divider()
        TextField(
            value = viewModel.tileLabel.value ?: "",
            onValueChange = { viewModel.tileLabel.value = it },
            label = {
                Text(
                    text = stringResource(id = R.string.tile_label)
                )
            },
            modifier = Modifier.padding(10.dp)
        )

        TextField(
            value = viewModel.tileSubtitle.value ?: "",
            onValueChange = { viewModel.tileSubtitle.value = it },
            label = {
                Text(
                    text = stringResource(id = R.string.tile_subtitle)
                )
            },
            modifier = Modifier.padding(10.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.tile_icon),
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            OutlinedButton(onClick = {
                iconDialog.show(childFragment, viewModel.selectedTile.value)
            }) {
                val icon = viewModel.drawableIcon.value?.let { DrawableCompat.wrap(it) }
                icon?.toBitmap()?.asImageBitmap()
                    ?.let {
                        Image(
                            it,
                            contentDescription = stringResource(id = R.string.tile_icon),
                            colorFilter = ColorFilter.tint(colorResource(R.color.colorAccent))
                        )
                    }
            }
        }

        Text(
            text = stringResource(id = R.string.tile_entity),
            fontSize = 15.sp
        )
        OutlinedButton(onClick = { expandedEntity = true }) {
            Text(text = viewModel.selectedEntityId.value)
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
        Button(
            onClick = {
                val tileData = TileEntity(
                    if (viewModel.currentTile() == null) 0 else viewModel.currentTile()!!.id,
                    viewModel.selectedTile.value,
                    viewModel.selectedIcon.value,
                    viewModel.selectedEntityId.value,
                    viewModel.tileLabel.value.toString(),
                    viewModel.tileSubtitle.value
                )
                AppDatabase.getInstance(context).tileDao().add(tileData)
                Toast.makeText(context, R.string.tile_updated, Toast.LENGTH_SHORT).show()
            },
            enabled = !viewModel.tileLabel.value.isNullOrEmpty() && viewModel.selectedEntityId.value.isNotEmpty()
        ) {
            Text(stringResource(id = R.string.tile_save))
        }
    }
}
