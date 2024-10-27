package io.homeassistant.companion.android.settings.developer.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class FeaturesSettingsFragment : Fragment() {

    private val viewModel: FeaturesSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    ScreenContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.feature_flag)
    }

    @Composable
    @Preview
    private fun ScreenContent() {
        val viewState by viewModel.viewStateFlow.collectAsState()

        FeatureList(viewState = viewState, interaction = viewModel)
        StringFeatureDialog(viewState = viewState, interaction = viewModel)
    }

    @Composable
    private fun FeatureList(
        modifier: Modifier = Modifier,
        viewState: FeaturesSettingsViewState,
        interaction: FeaturesSettingsInteraction
    ) {
        val lazyListState = rememberLazyListState()

        LazyColumn(
            modifier = modifier,
            state = lazyListState
        ) {
            items(viewState.features, { feature -> feature }) { feature ->
                when (feature) {
                    is Feature.StringFeature -> StringItem(feature = feature, interaction = interaction)
                    is Feature.BooleanFeature -> BooleanItem(feature = feature, interaction = interaction)
                }
                Divider(
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    @Composable
    private fun BooleanItem(
        modifier: Modifier = Modifier,
        feature: Feature.BooleanFeature,
        interaction: FeaturesSettingsInteraction
    ) {
        Row(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = feature.name
            )
            Switch(
                checked = feature.value,
                onCheckedChange = {
                    interaction.onBooleanFeatureChanged(feature, it)
                },
                enabled = feature.isUpdatable,
                colors = SwitchDefaults.colors(uncheckedThumbColor = colorResource(R.color.colorSwitchUncheckedThumb))
            )
        }
    }

    @Composable
    private fun StringItem(
        modifier: Modifier = Modifier,
        feature: Feature.StringFeature,
        interaction: FeaturesSettingsInteraction
    ) {
        Row(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = feature.name
            )
            TextButton(onClick = {
                interaction.onStringFeatureSelected(feature = feature)
            }, enabled = feature.isUpdatable) {
                Text(text = feature.value)
            }
        }
    }

    @Composable
    private fun StringFeatureDialog(viewState: FeaturesSettingsViewState, interaction: FeaturesSettingsViewModel) {
        viewState.selectedStringFeatures?.let { feature ->
            val text = remember { mutableStateOf(feature.value) }

            AlertDialog(
                onDismissRequest = {
                    interaction.onStringFeatureSelected(null)
                },
                dismissButton = {
                    Button(onClick = {
                        interaction.onStringFeatureSelected(null)
                    }) {
                        Text(stringResource(R.string.string_feature_flag_cancel))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        interaction.onStringFeatureChanged(feature, text.value)
                    }) {
                        Text(stringResource(R.string.string_feature_flag_update))
                    }
                },
                title = { Text(feature.name) },
                text = {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth(),
                        value = text.value,
                        onValueChange = { value ->
                            text.value = value
                        },
                        singleLine = true
                    )
                }
            )
        }
    }
}
