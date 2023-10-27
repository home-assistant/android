package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.settings.views.SettingsRow
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SettingsWearTemplateTileList(
    templateTiles: Map<Int?, TemplateTileConfig>,
    onTemplateTileClicked: (tileId: Int?) -> Unit,
    onBackClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            SettingsWearTopAppBar(
                title = { Text(stringResource(commonR.string.template_tiles)) },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(all = 16.dp)
        ) {
            if (templateTiles.entries.isEmpty()) {
                Text(stringResource(commonR.string.template_tile_no_tiles_yet))
            } else {
                var index = 1
                for (templateTileEntry in templateTiles.entries) {
                    SettingsRow(
                        primaryText = stringResource(commonR.string.template_tile_n, index++),
                        secondaryText = stringResource(commonR.string.template_tile_configure),
                        mdiIcon = CommunityMaterial.Icon3.cmd_text_box,
                        enabled = true,
                        onClicked = { onTemplateTileClicked(templateTileEntry.key) }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSettingsWearTemplateTileList() {
    SettingsWearTemplateTileList(
        templateTiles = mapOf(
            0 to TemplateTileConfig("Example entity 1: {{ states('sensor.example_entity_1') }}", 300),
            1 to TemplateTileConfig("Example entity 2: {{ states('sensor.example_entity_2') }}", 0)
        ),
        onTemplateTileClicked = {},
        onBackClicked = {}
    )
}

@Preview
@Composable
private fun PreviewSettingsWearTemplateTileListEmpty() {
    SettingsWearTemplateTileList(
        templateTiles = mapOf(),
        onTemplateTileClicked = {},
        onBackClicked = {}
    )
}
