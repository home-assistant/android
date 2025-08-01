package io.homeassistant.companion.android.webview.addto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.ModalBottomSheet

private sealed interface AddToType {
    val entityId: String

    data class WatchFavorite(override val entityId: String) : AddToType
    data class AutoFavorite(override val entityId: String) : AddToType
    data class ShortcutFavorite(override val entityId: String) : AddToType
    data class WidgetFavorite(override val entityId: String) : AddToType

    data class TileSetting(override val entityId: String) : AddToType
}

@AndroidEntryPoint
class AddToDialog(entityId: String, serverManager: ServerManager, prefsRepository: PrefsRepository) :
    BottomSheetDialogFragment() {

    val viewModel: AddToViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AddToViewModel.Factory> { factory ->
                factory.create(entityId, serverManager, prefsRepository)
            }
        },
    )

    companion object {
        const val TAG = "AddToDialog"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    val entityName by viewModel.entityName.collectAsState()
                    val potentialActions by viewModel.potentialActions.collectAsState()

                    AddToBottomSheet(
                        entityName,
                        potentialActions,
                        {
                            // TODO send to viemModel maybe or the action can contains already everything
                            when (it) {
                                AddToAction.AndroidAutoFavorite -> viewModel.addToAndroidAutoFavorite()
                                AddToAction.Shortcut -> TODO()
                                AddToAction.Tile -> {
                                    // TODO go to a new tile
//                                    startActivity(SettingsActivity.newInstance(requireContext(),
//                                        SettingsActivity.Deeplink.QSTile()
//                                    ))
                                }
                                AddToAction.Watch -> TODO()
                                AddToAction.Widget -> TODO()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToBottomSheet(
    entityName: String,
    potentialActions: List<AddToAction>,
    onAddSelected: (AddToAction) -> Unit,
) {
    // Handle cancel
    ModalBottomSheet(
        title = "Add entity ($entityName) to",
    ) {
        LazyColumn {
            items(potentialActions) {
                AddToRow(
                    text = it.text(),
                    image = ImageVector.vectorResource(it.imageRes),
                    onClick = { onAddSelected(it) },
                )
            }
        }
        Spacer(
            modifier = Modifier.height(24.dp),
        )
    }
}

@Composable
private fun AddToRow(text: String, image: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = image,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(4.dp),
        )
        Text(text = text, modifier = Modifier.padding(start = 8.dp))
    }
    Divider(modifier = Modifier.padding(horizontal = 16.dp))
}
