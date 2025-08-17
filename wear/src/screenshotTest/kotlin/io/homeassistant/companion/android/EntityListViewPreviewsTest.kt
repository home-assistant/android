package io.homeassistant.companion.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.home.views.EntityViewList
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2

class EntityListViewPreviewsTest {

    @PreviewTest
    @Preview(device = WearDevices.LARGE_ROUND)
    @Composable
    private fun PreviewEntityListView() {
        EntityViewList(
            entityLists = mapOf(stringResource(R.string.lights) to listOf(previewEntity1, previewEntity2)),
            entityListsOrder = listOf(stringResource(R.string.lights)),
            entityListFilter = { true },
            onEntityClicked = { _, _ -> },
            onEntityLongClicked = { },
            isHapticEnabled = false,
            isToastEnabled = false,
        )
    }
}
