package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HAButton
import io.homeassistant.companion.android.compose.composable.HATextButton
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.theme.HABorderWidth
import io.homeassistant.companion.android.onboarding.theme.HAColors
import io.homeassistant.companion.android.onboarding.theme.HARadius
import io.homeassistant.companion.android.onboarding.theme.HASpacing
import io.homeassistant.companion.android.onboarding.theme.HATextStyle
import io.homeassistant.companion.android.onboarding.theme.HATheme
import java.net.URL

@Composable
fun ServerDiscoveryScreen(
    modifier: Modifier = Modifier,
    onConnectClick: (server: URL) -> Unit = {},
    onManualSetupClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: ServerDiscoveryViewModel = hiltViewModel(),
) {
    val discoveryState by viewModel.discoveryStateFlow.collectAsStateWithLifecycle()

    ServerDiscoveryScreen(
        modifier = modifier,
        onConnectClick = onConnectClick,
        onManualSetupClick = onManualSetupClick,
        onHelpClick = onHelpClick,
        onBackClick = onBackClick,
        onDismissOneServerFound = viewModel::onDismissOneServerFound,
        discoveryState = discoveryState,
    )
}

@Composable
internal fun ServerDiscoveryScreen(
    onConnectClick: (server: URL) -> Unit,
    onManualSetupClick: () -> Unit,
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    onDismissOneServerFound: () -> Unit,
    discoveryState: DiscoveryState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        // The content will be scrollable behind the top bar
        modifier = modifier,
        topBar = {
            HATopBar(onBackClick = onBackClick, onHelpClick = onHelpClick)
        },
    ) { contentPadding ->
        ScreenContent(
            contentPadding = contentPadding,
            onManualSetupClick = onManualSetupClick,
            discoveryState = discoveryState,
            onConnectClick = onConnectClick,
        )

        if (discoveryState is ServerDiscovered) {
            OneServerFound(
                serverDiscovered = discoveryState,
                onDismiss = onDismissOneServerFound,
                onConnectClick = onConnectClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneServerFound(
    serverDiscovered: ServerDiscovered,
    onDismiss: () -> Unit,
    onConnectClick: (serverUrl: URL) -> Unit,
) {
    val bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false, initialValue = SheetValue.Expanded)

    ModalBottomSheet(
        sheetState = bottomSheetState,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = HARadius.M, topEnd = HARadius.M),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = HASpacing.XL)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HASpacing.XL),
        ) {
            Text(
                text = serverDiscovered.name,
                style = HATextStyle.Headline,
            )
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp), // TODO define the right size to use
                tint = HAColors.Brand.Blue,
            )
            Text(
                text = serverDiscovered.url.toString(),
                style = HATextStyle.Body,
                modifier = Modifier.padding(vertical = HASpacing.S),
            )
            HAButton(
                text = stringResource(R.string.welcome_connect_to_ha),
                onClick = {
                    onConnectClick(serverDiscovered.url)
                },
                modifier = Modifier.padding(bottom = HASpacing.XL),
            )
        }
    }
}

@Composable
private fun ScreenContent(
    contentPadding: PaddingValues,
    onManualSetupClick: () -> Unit,
    discoveryState: DiscoveryState,
    onConnectClick: (URL) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.searching_home_network),
            style = HATextStyle.Headline,
            modifier = Modifier.padding(top = HASpacing.XL),
        )

        when (discoveryState) {
            is Scanning, NoServerFound, is ServerDiscovered -> ScanningForServer(discoveryState)
            is ServersDiscovered -> ServersDiscoveredContent(discoveryState, onConnectClick)
        }

        HATextButton(
            text = stringResource(commonR.string.manual_setup),
            onClick = onManualSetupClick,
            modifier = Modifier.padding(bottom = HASpacing.XL),
        )
    }
}

@Composable
private fun ColumnScope.ServersDiscoveredContent(state: ServersDiscovered, onConnectClick: (URL) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .padding(top = HASpacing.X2L)
            .padding(horizontal = HASpacing.M),
    ) {
        items(state.servers) { server ->
            ServerItemContent(server, onConnectClick)
        }
        item {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HASpacing.X2L),
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ServerItemContent(server: ServerDiscovered, onConnectClick: (URL) -> Unit, modifier: Modifier = Modifier) {
    val rowShape = RoundedCornerShape(size = HARadius.XL)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HASpacing.XS)
            .border(
                border = BorderStroke(HABorderWidth.S, HAColors.Neutral80),
                shape = rowShape,
            )
            .clip(rowShape)
            .clickable(
                onClick = {
                    onConnectClick(server.url)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp) // TODO variable?
                .padding(vertical = HASpacing.XS, horizontal = HASpacing.M),
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(vertical = HASpacing.XS)
                .padding(end = HASpacing.M),
        ) {
            Text(
                text = server.name,
                style = HATextStyle.Body,
            )
            Text(
                text = server.url.toString(),
                style = HATextStyle.BodyMedium,
                // TODO color
            )
        }
    }
}

@Composable
private fun ColumnScope.ScanningForServer(discoveryState: DiscoveryState) {
    // We use spacer to position the image where we want when there is remaining space in the column using percentage
    val positionPercentage = 0.2f
    Spacer(modifier = Modifier.weight(positionPercentage))
    AnimatedIcon()
    Spacer(modifier = Modifier.weight(positionPercentage))

    val alpha: Float by animateFloatAsState(
        targetValue = if (discoveryState == NoServerFound) 1f else 0f,
        animationSpec = tween(
            durationMillis = 2000,
            easing = FastOutSlowInEasing,
        ),
    )

    Text(
        text = stringResource(R.string.server_discovery_no_server_info),
        style = HATextStyle.Body,
        modifier = Modifier
            .padding(
                // minimal padding that we want with the animation
                vertical = HASpacing.S,
                horizontal = HASpacing.M,
            )
            .alpha(alpha),
    )

    Spacer(modifier = Modifier.weight(1f - 2f * positionPercentage))
}

@Composable
private fun AnimatedIcon() {
    Box(modifier = Modifier.padding(HASpacing.S)) {
        val infiniteTransition = rememberInfiniteTransition(label = "dots_rotation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "dots_rotation_value",
        )
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.dots),
            contentDescription = null,
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.Center)
                .rotate(rotation),
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.Center)
                .background(HAColors.Brand.Blue, CircleShape),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(commonR.drawable.ic_stat_ic_notification_blue),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
                tint = HAColors.Brand.Background,
            )
        }
    }
}

@HAPreviews
@Composable
private fun ServerDiscoveryScreenPreview() {
    HATheme {
        ServerDiscoveryScreen()
    }
}

@HAPreviews
@Composable
private fun ServerDiscoveryScreenPreview_no_server_found() {
    HATheme {
        ServerDiscoveryScreen(
            discoveryState = NoServerFound,
            onConnectClick = {},
            onManualSetupClick = {},
            onHelpClick = {},
            onBackClick = {},
            onDismissOneServerFound = {},
        )
    }
}

@HAPreviews
@Composable
private fun ServerDiscoveryScreenPreview_with_one_server() {
    HATheme {
        ServerDiscoveryScreen(
            discoveryState = ServerDiscovered(
                "hello",
                URL("http://my.homeassistant.io"),
                HomeAssistantVersion(2042, 1, 42),
            ),
            onConnectClick = {},
            onManualSetupClick = {},
            onHelpClick = {},
            onBackClick = {},
            onDismissOneServerFound = {},
        )
    }
}

@HAPreviews
@Composable
private fun ServerDiscoveryScreenPreview_with_multiple_servers() {
    HATheme {
        ServerDiscoveryScreen(
            discoveryState = ServersDiscovered(
                listOf(
                    ServerDiscovered(
                        "hello",
                        URL("http://my.homeassistant.io"),
                        HomeAssistantVersion(2042, 1, 42),
                    ),
                    ServerDiscovered(
                        "world",
                        URL("http://my.homeassistant.io"),
                        HomeAssistantVersion(2042, 1, 42),
                    ),
                ),
            ),
            onConnectClick = {},
            onManualSetupClick = {},
            onHelpClick = {},
            onBackClick = {},
            onDismissOneServerFound = {},
        )
    }
}
