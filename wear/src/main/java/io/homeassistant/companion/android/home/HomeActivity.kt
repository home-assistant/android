package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
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
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.settings.ScreenSetFavorites
import io.homeassistant.companion.android.settings.ScreenSettings
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventHandlerSetup
import io.homeassistant.companion.android.util.RotaryEventState
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

    var expandedFavorites: Boolean by mutableStateOf(true)
    var expandedInputBooleans: Boolean by mutableStateOf(true)
    var expandedLights: Boolean by mutableStateOf(true)
    var expandedScenes: Boolean by mutableStateOf(true)
    var expandedScripts: Boolean by mutableStateOf(true)
    var expandedSwitches: Boolean by mutableStateOf(true)

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
            LoadHomePage(entities = entityViewModel.entitiesResponse, entityViewModel.favoriteEntities)
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
    private fun LoadHomePage(entities: Array<Entity<Any>>, favorites: MutableSet<String>) {

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
            val validEntities =
                entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] in HomePresenterImpl.supportedDomains }
            val scenes =
                entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "scene" }
            val scripts =
                entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "script" }
            val lights =
                entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "light" }
            val inputBooleans = entities.sortedBy { it.entityId }
                .filter { it.entityId.split(".")[0] == "input_boolean" }
            val switches =
                entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "switch" }

            val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
            RotaryEventDispatcher(scalingLazyListState)
            val swipeDismissableNavController = rememberSwipeDismissableNavController()

            MaterialTheme {
                Scaffold(
                    positionIndicator = {
                        if (scalingLazyListState.isScrollInProgress)
                            PositionIndicator(scalingLazyListState = scalingLazyListState)
                    }
                ) {
                    CompositionLocalProvider(
                        LocalRotaryEventDispatcher provides rotaryEventDispatcher
                    ) {
                        RotaryEventHandlerSetup(rotaryEventDispatcher)
                        SwipeDismissableNavHost(
                            navController = swipeDismissableNavController,
                            startDestination = SCREEN_LANDING
                        ) {
                            composable(SCREEN_LANDING) {
                                RotaryEventState(scrollState = scalingLazyListState)
                                ScalingLazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = 10.dp,
                                        start = 10.dp,
                                        end = 10.dp,
                                        bottom = 40.dp
                                    ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    state = scalingLazyListState
                                ) {
                                    if (favorites.isNotEmpty()) {
                                        item {
                                            SetListHeader(
                                                id = R.string.favorites,
                                                expanded = expandedFavorites
                                            )
                                        }
                                        val favoriteArray = favorites.toTypedArray()
                                        if (expandedFavorites) {
                                            items(favoriteArray.size) { index ->
                                                val favoriteEntityID =
                                                    favoriteArray[index].split(",")[0]
                                                val favoriteName =
                                                    favoriteArray[index].split(",")[1]
                                                val favoriteIcon =
                                                    favoriteArray[index].split(",")[2]
                                                if (entities.isNullOrEmpty()) {
                                                    // Use a normal chip when we don't have the state of the entity
                                                    Chip(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = if (index == 0) 0.dp else 10.dp),
                                                        icon = {
                                                            Image(
                                                                asset = getIcon(
                                                                    favoriteIcon,
                                                                    favoriteEntityID.split(".")[0],
                                                                    baseContext
                                                                )
                                                                    ?: CommunityMaterial.Icon.cmd_cellphone
                                                            )
                                                        },
                                                        label = {
                                                            Text(
                                                                text = favoriteName,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        },
                                                        onClick = {
                                                            presenter.onEntityClicked(
                                                                favoriteEntityID
                                                            )
                                                        },
                                                        colors = ChipDefaults.primaryChipColors(
                                                            backgroundColor = colorResource(id = R.color.colorAccent),
                                                            contentColor = Color.Black
                                                        )
                                                    )
                                                } else {
                                                    for (entity in entities) {
                                                        if (entity.entityId == favoriteEntityID) {
                                                            SetEntityUI(
                                                                entity = entity,
                                                                index = index
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (entities.isNullOrEmpty()) {
                                        item {
                                            Column {
                                                SetTitle(id = R.string.loading)
                                                Chip(
                                                    modifier = Modifier
                                                        .padding(
                                                            top = 10.dp,
                                                            start = 10.dp,
                                                            end = 10.dp
                                                        ),
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
                                        }
                                    }
                                    if (inputBooleans.isNotEmpty()) {
                                        item {
                                            SetListHeader(
                                                id = R.string.input_booleans,
                                                expanded = expandedInputBooleans
                                            )
                                        }
                                        if (expandedInputBooleans) {
                                            items(inputBooleans.size) { index ->
                                                SetEntityUI(inputBooleans[index], index)
                                            }
                                        }
                                    }
                                    if (lights.isNotEmpty()) {
                                        item {
                                            SetListHeader(
                                                id = R.string.lights,
                                                expanded = expandedLights
                                            )
                                        }
                                        if (expandedLights) {
                                            items(lights.size) { index ->
                                                SetEntityUI(lights[index], index)
                                            }
                                        }
                                    }
                                    if (scenes.isNotEmpty()) {
                                        item {
                                            SetListHeader(
                                                id = R.string.scenes,
                                                expanded = expandedScenes
                                            )
                                        }
                                        if (expandedScenes) {
                                            items(scenes.size) { index ->
                                                SetEntityUI(scenes[index], index)
                                            }
                                        }
                                    }
                                    if (scripts.isNotEmpty()) {
                                        item {
                                            SetListHeader(
                                                id = R.string.scripts,
                                                expanded = expandedScripts
                                            )
                                        }
                                        if (expandedScripts) {
                                            items(scripts.size) { index ->
                                                SetEntityUI(scripts[index], index)
                                            }
                                        }
                                    }
                                    if (switches.isNotEmpty()) {
                                        item {
                                            SetListHeader(
                                                id = R.string.switches,
                                                expanded = expandedSwitches
                                            )
                                        }
                                        if (expandedSwitches) {
                                            items(switches.size) { index ->
                                                SetEntityUI(switches[index], index)
                                            }
                                        }
                                    }
                                    item {
                                        LoadOtherSection(swipeDismissableNavController)
                                    }
                                }
                            }
                            composable(SCREEN_SETTINGS) {
                                ScreenSettings(
                                    swipeDismissableNavController,
                                    entityViewModel,
                                    presenter
                                )
                            }
                            composable(SCREEN_SET_FAVORITES) {
                                ScreenSetFavorites(
                                    validEntities,
                                    entityViewModel,
                                    baseContext,
                                    presenter
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetEntityUI(entity: Entity<Any>, index: Int) {
        val attributes = entity.attributes as Map<String, String>
        val iconBitmap = getIcon(attributes["icon"], entity.entityId.split(".")[0], baseContext)

        if (entity.entityId.split(".")[0] in HomePresenterImpl.toggleDomains) {
            ToggleChip(
                checked = entity.state == "on",
                onCheckedChange = {
                    presenter.onEntityClicked(entity.entityId)
                    updateEntities()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 0.dp else 10.dp),
                appIcon = { Image(asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone) },
                label = {
                    Text(
                        text = attributes["friendly_name"].toString(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                enabled = entity.state != "unavailable",
                toggleIcon = { ToggleChipDefaults.SwitchIcon(entity.state == "on") },
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedStartBackgroundColor = colorResource(id = R.color.colorAccent),
                    checkedEndBackgroundColor = colorResource(id = R.color.colorAccent),
                    uncheckedStartBackgroundColor = colorResource(id = R.color.colorAccent),
                    uncheckedEndBackgroundColor = colorResource(id = R.color.colorAccent),
                    checkedContentColor = Color.Black,
                    uncheckedContentColor = Color.Black,
                    checkedToggleIconTintColor = Color.Yellow,
                    uncheckedToggleIconTintColor = Color.DarkGray
                )
            )
        } else {
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 0.dp else 10.dp),
                icon = { Image(asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone) },
                label = {
                    Text(
                        text = attributes["friendly_name"].toString(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                enabled = entity.state != "unavailable",
                onClick = {
                    presenter.onEntityClicked(entity.entityId)
                    updateEntities()
                },
                colors = setChipDefaults()
            )
        }
    }

    @Composable
    private fun LoadOtherSection(swipeDismissableNavController: NavHostController) {
        Column {
            SetTitle(R.string.other)
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                icon = {
                    Image(asset = CommunityMaterial.Icon.cmd_cog)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.settings)
                    )
                },
                onClick = { swipeDismissableNavController.navigate(SCREEN_SETTINGS) },
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                )
            )
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                icon = {
                    Image(asset = CommunityMaterial.Icon.cmd_exit_run)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.logout)
                    )
                },
                onClick = { presenter.onLogoutClicked() },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = Color.Red,
                    contentColor = Color.Black
                )
            )
        }
    }

    private fun updateEntities() {
        mainScope.launch {
            entityViewModel.entitiesResponse = presenter.getEntities()
            delay(5000L)
            entityViewModel.entitiesResponse = presenter.getEntities()
        }
    }

    @Composable
    private fun SetListHeader(id: Int, expanded: Boolean) {
        ListHeader(
            modifier = Modifier
                .clickable {
                    when (id) {
                        R.string.favorites -> expandedFavorites = !expanded
                        R.string.input_booleans -> expandedInputBooleans = !expanded
                        R.string.lights -> expandedLights = !expanded
                        R.string.scenes -> expandedScenes = !expanded
                        R.string.scripts -> expandedScripts = !expanded
                        R.string.switches -> expandedSwitches = !expanded
                    }
                }
        ) {
            Row {
                Text(
                    text = stringResource(id = id) + if (expanded) " -" else " +",
                    color = Color.White
                )
            }
        }
    }
}
