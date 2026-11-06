package io.homeassistant.companion.android.util.icondialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

@Composable
fun IconDialogContentM3(
    modifier: Modifier = Modifier,
    iconFilter: IconFilter = DefaultIconFilter(),
    onSelect: (IIcon) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    Column(modifier = modifier) {
        IconDialogSearchM3(
            value = searchQuery,
            onValueChange = { searchQuery = it },
        )
        IconDialogGridM3(
            typeface = CommunityMaterial,
            searchQuery = searchQuery,
            iconFilter = iconFilter,
            onClick = onSelect,
        )
    }
}

@Composable
fun IconDialogM3(
    onSelect: (IIcon) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    iconFilter: IconFilter = DefaultIconFilter(),
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .width(480.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            IconDialogContentM3(
                iconFilter = iconFilter,
                onSelect = onSelect,
            )
        }
    }
}

@Preview
@Composable
private fun IconDialogM3Preview() {
    HAThemeForPreview {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            IconDialogContentM3(onSelect = {})
        }
    }
}
