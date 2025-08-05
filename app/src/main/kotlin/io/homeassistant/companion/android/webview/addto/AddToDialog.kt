package io.homeassistant.companion.android.webview.addto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.ModalBottomSheet

@AndroidEntryPoint
class AddToDialog(private val entityId: String, serverManager: ServerManager, prefsRepository: PrefsRepository) :
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
                    val potentialActions by viewModel.potentialActions.collectAsState()

                    AddToBottomSheet(
                        potentialActions,
                        {
                            it.action(requireContext(), viewModel, entityId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
@VisibleForTesting
fun AddToBottomSheet(potentialActions: List<AddToAction>, onAddSelected: (AddToAction) -> Unit) {
    // Handle cancel
    ModalBottomSheet(title = null) {
        LazyColumn {
            items(potentialActions) {
                AddToRow(
                    text = it.text(),
                    icon = { modifier -> it.Icon(modifier) },
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
private fun AddToRow(text: String, icon: @Composable (modifier: Modifier) -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        icon(
            Modifier
                .size(24.dp)
                .padding(4.dp),
        )
        Text(text = text, modifier = Modifier.padding(start = 8.dp))
    }
    Divider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
@Preview
private fun AddToBottomSheetPreview() {
    HomeAssistantAppTheme {
        AddToBottomSheet(
            listOf(
                AddToAction.MediaPlayerWidget,
                AddToAction.TodoWidget,
                AddToAction.EntityWidget,
                AddToAction.CameraWidget,
                AddToAction.Tile,
                AddToAction.Shortcut,
                AddToAction.Watch("Hello"),
            ),
        ) {}
    }
}
