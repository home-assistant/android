package io.homeassistant.companion.android.settings.qs.views

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun ManageTilesView(
    viewModel: ManageTilesViewModel,
    onShowIconDialog: (tag: String?) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var expandedTile by remember { mutableStateOf(false) }
    var expandedEntity by remember { mutableStateOf(false) }

    val scaffoldState = rememberScaffoldState()
    LaunchedEffect("snackbar") {
        viewModel.saveResultSnackbar.onEach {
            if (it != 0) {
                scaffoldState.snackbarHostState.showSnackbar(context.getString(it))
            }
        }.launchIn(this)
    }

    Scaffold(scaffoldState = scaffoldState) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(scrollState)
        ) {
            Column(modifier = Modifier.padding(all = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.tile_select),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Box {
                        OutlinedButton(onClick = { expandedTile = true }) {
                            Text(viewModel.selectedTile.name)
                        }

                        DropdownMenu(expanded = expandedTile, onDismissRequest = { expandedTile = false }) {
                            for ((index, slot) in viewModel.slots.withIndex()) {
                                DropdownMenuItem(onClick = {
                                    viewModel.selectTile(index)
                                    expandedTile = false
                                }) {
                                    Text(slot.name)
                                }
                            }
                        }
                    }
                }

                Divider()
                TextField(
                    value = viewModel.tileLabel,
                    onValueChange = { viewModel.tileLabel = it },
                    label = {
                        Text(text = stringResource(id = R.string.tile_label))
                    },
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth()
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    TextField(
                        value = viewModel.tileSubtitle.orEmpty(),
                        onValueChange = { viewModel.tileSubtitle = it },
                        label = {
                            Text(text = stringResource(id = R.string.tile_subtitle))
                        },
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(id = R.string.tile_icon),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    OutlinedButton(
                        onClick = { onShowIconDialog(viewModel.selectedTile.id) }
                    ) {
                        val iconBitmap = remember(viewModel.selectedIconDrawable) {
                            viewModel.selectedIconDrawable?.toBitmap()?.asImageBitmap()
                        }
                        iconBitmap?.let {
                            Image(
                                iconBitmap,
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
                    Text(text = viewModel.selectedEntityId)
                }

                DropdownMenu(expanded = expandedEntity, onDismissRequest = { expandedEntity = false }) {
                    for (item in viewModel.sortedEntities) {
                        DropdownMenuItem(onClick = {
                            viewModel.selectedEntityId = item.entityId
                            expandedEntity = false
                        }) {
                            Text(text = item.entityId, fontSize = 15.sp)
                        }
                    }
                }
                Button(
                    onClick = { viewModel.addTile() },
                    enabled = viewModel.tileLabel.isNotBlank() && viewModel.selectedEntityId.isNotBlank()
                ) {
                    Text(stringResource(viewModel.submitButtonLabel))
                }
            }
        }
    }
}
