package io.homeassistant.companion.android.util.icondialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@Composable
fun IconDialogContent(iconFilter: IconFilter = DefaultIconFilter(), onSelect: (IIcon) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    Column {
        IconDialogSearch(value = searchQuery, onValueChange = { searchQuery = it })
        IconDialogGrid(
            typeface = CommunityMaterial,
            searchQuery = searchQuery,
            iconFilter = iconFilter,
            onClick = onSelect,
        )
    }
}

@Composable
fun IconDialog(iconFilter: IconFilter = DefaultIconFilter(), onSelect: (IIcon) -> Unit, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            IconDialogContent(iconFilter = iconFilter, onSelect = onSelect)
        }
    }
}

@Preview
@Composable
private fun IconDialogPreview() {
    HomeAssistantAppTheme {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            IconDialogContent(onSelect = {})
        }
    }
}
