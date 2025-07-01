package io.homeassistant.companion.android.onboarding.serverdiscovery

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.R
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
    val discoveryState by remember { viewModel.discoveryState }

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
    discoveryState: DiscoveryState?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        // The content will be scrollable behind the top bar
        modifier = modifier,
        topBar = {
            TopBar(onBackClick = onBackClick, onHelpClick = onHelpClick)
        },
    ) { contentPadding ->
        // TODO this could depends on the state too
        ScreenContent(contentPadding, onManualSetupClick)

        when (discoveryState) {
            is ServerDiscovered -> OneServerFound(discoveryState, onDismiss = onDismissOneServerFound, onConnectClick = onConnectClick)
            // MultipleServersFound(serverDiscovered as ServersDiscovered)
            is ServersDiscovered -> {}
            NoServerFound -> {}
            null -> {
                /* Nothing to do */
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(commonR.string.navigate_up),
                )
            }
        },
        actions = {
            IconButton(onClick = onHelpClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(commonR.string.get_help),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneServerFound(serverDiscovered: ServerDiscovered, onDismiss: () -> Unit, onConnectClick: (serverUrl: URL) -> Unit) {
    val bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = true, initialValue = SheetValue.Expanded)

    ModalBottomSheet(
        sheetState = bottomSheetState,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = HARadius.M, topEnd = HARadius.M),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = HASpacing.XL)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = serverDiscovered.name,
                style = HATextStyle.Headline,
                modifier = Modifier.padding(vertical = HASpacing.S),
            )
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp) // TODO define the right size to use
                    .padding(vertical = HASpacing.S),
                tint = HAColors.Brand.Blue,
            )
            Text(
                text = serverDiscovered.url.toString(),
                style = HATextStyle.Body,
                modifier = Modifier.padding(vertical = HASpacing.S),
            )
            Button(
                onClick = {
                    onConnectClick(serverDiscovered.url)
                },
                modifier = Modifier.padding(vertical = HASpacing.S),
                contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
                shape = RoundedCornerShape(size = HARadius.XL),
            ) {
                Text(
                    text = stringResource(R.string.welcome_connect_to_ha),
                    style = HATextStyle.Button,
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(contentPadding: PaddingValues, onManualSetupClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()) // Apply only top padding
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.searching_home_network),
            style = HATextStyle.Headline,
        )

        // We use spacer to position the image where we want when there is remaining space in the column using percentage
        val positionPercentage = 0.2f
        Spacer(modifier = Modifier.weight(positionPercentage))
        AnimatedIcon()
        Spacer(modifier = Modifier.weight(1f - positionPercentage))

        TextButton(
            onClick = onManualSetupClick,
            modifier = Modifier.padding(bottom = HASpacing.XL),
            contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
            shape = RoundedCornerShape(size = HARadius.XL),
        ) {
            Text(
                text = stringResource(commonR.string.manual_setup),
                style = HATextStyle.Button,
            )
        }
    }
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
private fun ServerDiscoveryScreen_with_one_server_Preview() {
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
