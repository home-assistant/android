package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
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
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventHandlerSetup
import io.homeassistant.companion.android.util.RotaryEventState
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
        private const val SCREEN_SETTINGS = "settings"
        private const val SCREEN_SET_FAVORITES = "set_favorites"

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
        updateFavorites()
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
            updateFavorites()
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
                        RotaryEventState(scrollState = scalingLazyListState)
                        SwipeDismissableNavHost(
                            navController = swipeDismissableNavController,
                            startDestination = SCREEN_LANDING
                        ) {
                            composable(SCREEN_LANDING) {
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
                                        val favoriteArray = favorites.toTypedArray()
                                        items(favoriteArray.size) { index ->
                                            Spacer(modifier = Modifier.height(10.dp))
                                            if (index == 0)
                                                SetTitle(id = R.string.favorites)
                                            val favoriteEntityID =
                                                favoriteArray[index].split(",")[0]
                                            val favoriteName = favoriteArray[index].split(",")[1]
                                            val favoriteIcon = favoriteArray[index].split(",")[2]
                                            if (entities.isNullOrEmpty()) {
                                                // Use a normal chip when we don't have the state of the entity
                                                Chip(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = if (index == 0) 30.dp else 10.dp),
                                                    icon = {
                                                        Image(
                                                            asset = getIcon(
                                                                favoriteIcon,
                                                                favoriteEntityID.split(".")[0]
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
                                                        SetEntityUI(entity = entity, index = index)
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
                                        items(inputBooleans.size) { index ->
                                            if (index == 0)
                                                SetTitle(R.string.input_booleans)
                                            SetEntityUI(inputBooleans[index], index)
                                        }
                                    }
                                    if (lights.isNotEmpty()) {
                                        items(lights.size) { index ->
                                            if (index == 0)
                                                SetTitle(R.string.lights)
                                            SetEntityUI(lights[index], index)
                                        }
                                    }
                                    if (scenes.isNotEmpty()) {
                                        items(scenes.size) { index ->
                                            if (index == 0)
                                                SetTitle(R.string.scenes)

                                            SetEntityUI(scenes[index], index)
                                        }
                                    }
                                    if (scripts.isNotEmpty()) {
                                        items(scripts.size) { index ->
                                            if (index == 0)
                                                SetTitle(R.string.scripts)
                                            SetEntityUI(scripts[index], index)
                                        }
                                    }
                                    if (switches.isNotEmpty()) {
                                        items(switches.size) { index ->
                                            if (index == 0)
                                                SetTitle(R.string.switches)
                                            SetEntityUI(switches[index], index)
                                        }
                                    }

                                    item {
                                        LoadOtherSection(swipeDismissableNavController)
                                    }
                                }
                            }
                            composable(SCREEN_SETTINGS) {
                                ScreenSettings(swipeDismissableNavController)
                            }
                            composable(SCREEN_SET_FAVORITES) {
                                ScreenSetFavorites(validEntities, scalingLazyListState)
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
        val iconBitmap = getIcon(attributes["icon"], entity.entityId.split(".")[0])

        if (entity.entityId.split(".")[0] in HomePresenterImpl.toggleDomains) {
            ToggleChip(
                checked = entity.state == "on",
                onCheckedChange = {
                    presenter.onEntityClicked(entity.entityId)
                    updateEntities()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 30.dp else 10.dp),
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
                    .padding(top = if (index == 0) 30.dp else 10.dp),
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
    private fun SetTitle(id: Int) {
        Text(
            text = stringResource(id = id),
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
        )
    }

    @Composable
    private fun setChipDefaults(): ChipColors {
        return ChipDefaults.primaryChipColors(
            backgroundColor = colorResource(id = R.color.colorAccent),
            contentColor = Color.Black
        )
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
                onClick = { swipeDismissableNavController.navigate("Settings") },
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

    @Composable
    private fun ScreenSettings(swipeDismissableNavController: NavHostController) {
        Column {
            Spacer(modifier = Modifier.height(20.dp))
            SetTitle(id = R.string.settings)
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                icon = {
                    Image(asset = CommunityMaterial.Icon3.cmd_star)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.favorite)
                    )
                },
                onClick = {
                    swipeDismissableNavController.navigate(
                        SCREEN_SET_FAVORITES
                    )
                },
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                )
            )
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                icon = {
                    Image(asset = CommunityMaterial.Icon.cmd_delete)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.clear_favorites),
                    )
                },
                onClick = {
                    entityViewModel.favoriteEntities = mutableSetOf()
                    saveFavorites(entityViewModel.favoriteEntities.toMutableSet())
                },
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                ),
                secondaryLabel = {
                    Text(
                        text = stringResource(id = R.string.irreverisble)
                    )
                },
                enabled = entityViewModel.favoriteEntities.isNotEmpty()
            )
        }
    }

    @Composable
    private fun ScreenSetFavorites(validEntities: List<Entity<Any>>, scalingLazyListState: ScalingLazyListState) {
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
            items(validEntities.size) { index ->
                val attributes = validEntities[index].attributes as Map<String, String>
                val iconBitmap = getIcon(attributes["icon"], validEntities[index].entityId.split(".")[0])
                if (index == 0)
                    SetTitle(id = R.string.set_favorite)
                val elementString = "${validEntities[index].entityId},${attributes["friendly_name"]},${attributes["icon"]}"
                var checked by rememberSaveable { mutableStateOf(entityViewModel.favoriteEntities.contains(elementString)) }
                ToggleChip(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        if (it) {
                            entityViewModel.favoriteEntities.add(elementString)
                        } else {
                            entityViewModel.favoriteEntities.remove(elementString)
                        }
                        saveFavorites(entityViewModel.favoriteEntities.toMutableSet())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 30.dp else 10.dp),
                    appIcon = { Image(asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone) },
                    label = {
                        Text(
                            text = attributes["friendly_name"].toString(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleIcon = { ToggleChipDefaults.SwitchIcon(checked) },
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

    private fun updateFavorites() {
        mainScope.launch { entityViewModel.favoriteEntities = presenter.getWearHomeFavorites().toMutableSet() }
    }

    private fun saveFavorites(favorites: Set<String>) {
        mainScope.launch { presenter.setWearHomeFavorites(favorites.toSet()) }
    }

    private fun getIcon(icon: String?, domain: String): IIcon? {
        return if (icon?.startsWith("mdi") == true) {
            val mdiIcon = icon.split(":")[1]
            IconicsDrawable(baseContext, "cmd-$mdiIcon").icon
        } else {
            when (domain) {
                "input_boolean", "switch" -> CommunityMaterial.Icon2.cmd_light_switch
                "light" -> CommunityMaterial.Icon2.cmd_lightbulb
                "script" -> CommunityMaterial.Icon3.cmd_script_text_outline
                "scene" -> CommunityMaterial.Icon3.cmd_palette_outline
                else -> CommunityMaterial.Icon.cmd_cellphone
            }
        }
    }
}
