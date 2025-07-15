package io.homeassistant.companion.android.onboarding.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.OnboardingHeaderView
import io.homeassistant.companion.android.onboarding.OnboardingScreen
import io.homeassistant.companion.android.util.homeAssistantInstance1
import io.homeassistant.companion.android.util.homeAssistantInstance2
import kotlinx.coroutines.delay

@Composable
fun DiscoveryView(
    discoveryActive: Boolean,
    foundInstances: SnapshotStateList<HomeAssistantInstance>,
    manualSetupClicked: () -> Unit,
    instanceClicked: (instance: HomeAssistantInstance) -> Unit,
) {
    var discoveryTimeout by remember { mutableStateOf(false) }
    LaunchedEffect("discoveryTimeout") {
        delay(10_000L)
        discoveryTimeout = true
    }

    OnboardingScreen {
        OnboardingHeaderView(
            icon = CommunityMaterial.Icon2.cmd_home_search,
            title = stringResource(id = commonR.string.select_instance),
        )
        val indicatorModifier = Modifier
            .fillMaxWidth(0.25f)
            .padding(vertical = 16.dp)
            .height(2.dp)
            .align(Alignment.CenterHorizontally)
        if (discoveryActive) {
            LinearProgressIndicator(modifier = indicatorModifier)
        } else {
            LinearProgressIndicator(progress = 0f, modifier = indicatorModifier)
        }

        val discoveryHasError by remember(discoveryActive, foundInstances.size, discoveryTimeout) {
            mutableStateOf(!discoveryActive || (foundInstances.size == 0 && discoveryTimeout))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(foundInstances.size, { foundInstances[it].url }) { index ->
                val instance = foundInstances[index]
                DiscoveredInstanceRow(
                    instance = instance,
                    onClick = { instanceClicked(instance) },
                )
            }
            item("discovery.error") {
                AnimatedVisibility(discoveryHasError) {
                    Text(
                        text = stringResource(
                            if (!discoveryActive) commonR.string.failed_scan else commonR.string.slow_scan,
                        ),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (discoveryHasError && discoveryActive) {
                Text(
                    text = stringResource(commonR.string.manual_setup_hint),
                    style = MaterialTheme.typography.body2,
                )
            }
            if (discoveryHasError) {
                OutlinedButton(onClick = manualSetupClicked) {
                    Text(text = stringResource(commonR.string.manual_setup))
                }
            } else {
                TextButton(onClick = manualSetupClicked) {
                    Text(text = stringResource(commonR.string.manual_setup))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredInstanceRow(instance: HomeAssistantInstance, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
    ) {
        Column {
            Text(instance.name)
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = instance.url.toString(),
                    fontSize = 14.sp,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Default.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(24.dp).padding(4.dp),
        )
    }
    Divider(Modifier.padding(8.dp))
}

@Preview(showSystemUi = true)
@Composable
fun DiscoveryViewActivePreview() {
    DiscoveryView(
        discoveryActive = true,
        foundInstances = remember {
            mutableStateListOf(homeAssistantInstance1, homeAssistantInstance2)
        },
        manualSetupClicked = { },
        instanceClicked = {},
    )
}

@Preview(showSystemUi = true)
@Composable
fun DiscoveryViewErrorPreview() {
    DiscoveryView(
        discoveryActive = false,
        foundInstances = remember { mutableStateListOf() },
        manualSetupClicked = { },
        instanceClicked = {},
    )
}
