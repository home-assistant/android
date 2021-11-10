package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.home.views.LandingScreen
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.settings.ScreenSetFavorites
import io.homeassistant.companion.android.settings.ScreenSettings
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventHandlerSetup
import io.homeassistant.companion.android.util.SetTitle
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.setChipDefaults
import io.homeassistant.companion.android.util.updateFavorites
import io.homeassistant.companion.android.viewModels.EntityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeActivity : ComponentActivity(), HomeView {

    @Inject
    lateinit var presenter: HomePresenter

    private val entityViewModel by viewModels<EntityViewModel>()
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "HomeActivity"
        private const val SCREEN_LANDING = "landing"
        const val SCREEN_SETTINGS = "settings"
        const val SCREEN_SET_FAVORITES = "set_favorites"

        fun newInstance(context: Context): Intent {
            return Intent(context, HomeActivity::class.java)
        }
    }

    @ExperimentalWearMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        presenter.onViewReady()
        updateEntities()
        updateFavorites(entityViewModel, presenter, mainScope)
        setContent {
            LoadHomePage(
                entities = entityViewModel.entitiesResponse,
                entityViewModel.favoriteEntities
            )
        }
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun displayOnBoarding() {
        val intent = OnboardingActivity.newInstance(this)
        startActivity(intent)
        finish()
    }

    override fun displayMobileAppIntegration() {
        val intent = MobileAppIntegrationActivity.newInstance(this)
        startActivity(intent)
        finish()
    }

    @ExperimentalWearMaterialApi
    @Composable
    private fun LoadHomePage(entities: List<Entity<Any>>, favorites: MutableSet<String>) {

        val rotaryEventDispatcher = RotaryEventDispatcher()
        if (entities.isNullOrEmpty() && favorites.isNullOrEmpty()) {
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
            updateFavorites(entityViewModel, presenter, mainScope)

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
                            LandingScreen(
                                entities.toMutableList(),
                                favorites,
                                { presenter.onEntityClicked(it) },
                                { swipeDismissableNavController.navigate(SCREEN_SETTINGS) },
                                { presenter.onLogoutClicked() }
                            )
                        }
                        composable(SCREEN_SETTINGS) {
                            ScreenSettings(
                                swipeDismissableNavController,
                                entityViewModel,
                                presenter
                            )
                        }
                        composable(SCREEN_SET_FAVORITES) {
                            val validEntities =
                                entities.sortedBy { it.entityId }
                                    .filter { it.entityId.split(".")[0] in HomePresenterImpl.supportedDomains }
                            ScreenSetFavorites(
                                validEntities,
                                entityViewModel,
                                presenter
                            )
                        }
                    }
                }

            }
        }
    }


    private fun updateEntities() {
        mainScope.launch {
            entityViewModel.entitiesResponse = presenter.getEntities()
            delay(5000L)
            entityViewModel.entitiesResponse = presenter.getEntities()
        }
    }

}
