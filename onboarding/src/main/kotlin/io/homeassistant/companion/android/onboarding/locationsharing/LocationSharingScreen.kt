package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.theme.HASpacing
import io.homeassistant.companion.android.onboarding.theme.HATheme

@Composable
fun LocationSharingScreen(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: LocationSharingViewModel = hiltViewModel(),
) {
    LocationSharingScreen(
        onHelpClick = onHelpClick,
        onBackClick = onBackClick,
        modifier = Modifier,
    )
}

@Composable
fun LocationSharingScreen(onHelpClick: () -> Unit, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick, onBackClick = onBackClick) },
    ) { contentPadding ->
        LocationSharingScreenContent(
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun LocationSharingScreenContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HASpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HASpacing.XL),
    ) {
        Image(
            modifier = Modifier.padding(top = HASpacing.XL),
            imageVector = ImageVector.vectorResource(R.drawable.ic_location_tracking),
            contentDescription = null,
        )
    }
}

@HAPreviews
@Composable
private fun LocationSharingScreenPreview() {
    HATheme {
        LocationSharingScreen(
            onHelpClick = {},
            onBackClick = {},
            modifier = Modifier,
        )
    }
}
