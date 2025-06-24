package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.settings.views.SettingsRow
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun SettingsWearTemplateTileList(
    templateTiles: Map<Int, TemplateTileConfig>,
    onTemplateTileClicked: (tileId: Int) -> Unit,
    onBackClicked: () -> Unit,
) {
    Scaffold(
        topBar = {
            SettingsWearTopAppBar(
                title = { Text(stringResource(commonR.string.template_tiles)) },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(safeBottomPaddingValues())
                .padding(padding),
        ) {
            if (templateTiles.entries.isEmpty()) {
                Text(
                    text = stringResource(commonR.string.template_tile_no_tiles_yet),
                    modifier = Modifier
                        .padding(all = 16.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(start = 72.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = commonR.string.template_tile_configure),
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                    )
                }

                var index = 1
                for (templateTileEntry in templateTiles.entries) {
                    val template = templateTileEntry.value.template
                    SettingsRow(
                        primaryText = stringResource(commonR.string.template_tile_n, index++),
                        secondaryText = when {
                            template.length <= 100 -> template
                            else -> "${template.take(100)}…"
                        },
                        mdiIcon = CommunityMaterial.Icon3.cmd_text_box,
                        enabled = true,
                        onClicked = { onTemplateTileClicked(templateTileEntry.key) },
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
            123 to TemplateTileConfig("Example entity 1: {{ states('sensor.example_entity_1') }}", 300),
            51468 to TemplateTileConfig("Example entity 2: {{ states('sensor.example_entity_2') }}", 0),
        ),
        onTemplateTileClicked = {},
        onBackClicked = {},
    )
}

@Preview
@Composable
private fun PreviewSettingsWearTemplateSingleLegacyTile() {
    SettingsWearTemplateTileList(
        templateTiles = mapOf(
            -1 to TemplateTileConfig("Example entity 1: {{ states('sensor.example_entity_1') }}", 300),
        ),
        onTemplateTileClicked = {},
        onBackClicked = {},
    )
}

@Preview
@Composable
private fun PreviewSettingsWearTemplateTileListEmpty() {
    SettingsWearTemplateTileList(
        templateTiles = mapOf(),
        onTemplateTileClicked = {},
        onBackClicked = {},
    )
}
