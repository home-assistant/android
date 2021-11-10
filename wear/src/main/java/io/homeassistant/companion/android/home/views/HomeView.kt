package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventHandlerSetup
import io.homeassistant.companion.android.util.SetTitle
import io.homeassistant.companion.android.util.setChipDefaults

private const val SCREEN_LANDING = "landing"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_SET_FAVORITES = "set_favorites"

@ExperimentalWearMaterialApi
@Composable
fun LoadHomePage(
    mainViewModel: MainViewModel
) {

    val rotaryEventDispatcher = RotaryEventDispatcher()
    if (mainViewModel.entities.isNullOrEmpty() && mainViewModel.favoriteEntityIds.isNullOrEmpty()) {
        Column {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
            SetTitle(id = R.string.loading)
            Chip(
                modifier = Modifier
                    .padding(top = 50.dp, start = 10.dp, end = 10.dp),
                label = {
                    Text(
                        text = stringResource(R.string.loading_entities),
                        textAlign = TextAlign.Center
                    )
                },
                onClick = { /* No op */ },
                colors = setChipDefaults()
            )
        }
    } else {
        val swipeDismissableNavController = rememberSwipeDismissableNavController()
        MaterialTheme {
            CompositionLocalProvider(
                LocalRotaryEventDispatcher provides rotaryEventDispatcher
            ) {
                RotaryEventHandlerSetup(rotaryEventDispatcher)
                SwipeDismissableNavHost(
                    navController = swipeDismissableNavController,
                    startDestination = SCREEN_LANDING
                ) {
                    composable(SCREEN_LANDING) {
                        MainView(
                            mainViewModel.entities,
                            mainViewModel.favoriteEntityIds,
                            { mainViewModel.toggleEntity(it) },
                            { swipeDismissableNavController.navigate(SCREEN_SETTINGS) },
                            { mainViewModel.logout() }
                        )
                    }
                    composable(SCREEN_SETTINGS) {
                        SettingsView(
                            mainViewModel.favoriteEntityIds,
                            { swipeDismissableNavController.navigate(SCREEN_SET_FAVORITES) },
                            { mainViewModel.clearFavorites() }
                        )
                    }
                    composable(SCREEN_SET_FAVORITES) {
                        val validEntities = mainViewModel.entities
                            .filter { it.entityId.split(".")[0] in HomePresenterImpl.supportedDomains }
                        SetFavoritesView(
                            validEntities,
                            mainViewModel.favoriteEntityIds
                        ) { entityId, isSelected ->
                            if (isSelected) {
                                mainViewModel.addFavorite(entityId)
                            } else {
                                mainViewModel.removeFavorite(entityId)
                            }
                        }
                    }
                }
            }
        }
    }
}
