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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
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

        fun newInstance(context: Context): Intent {
            return Intent(context, HomeActivity::class.java)
        }
    }

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
        setContent {
            LoadHomePage(entities = entityViewModel.entitiesResponse)
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

    @Composable
    private fun LoadHomePage(entities: Array<Entity<Any>>) {

        if (entities.isNullOrEmpty()) {
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

            MaterialTheme {
                Scaffold(
                    positionIndicator = {
                        if (scalingLazyListState.isScrollInProgress)
                            PositionIndicator(scalingLazyListState = scalingLazyListState)
                    }
                ) {
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
                            Column {
                                SetTitle(R.string.other)
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
                    }
                }
            }
        }
    }

    @Composable
    private fun SetEntityUI(entity: Entity<Any>, index: Int) {
        val attributes = entity.attributes as Map<String, String>
        val iconBitmap =
            if (attributes["icon"]?.startsWith("mdi") == true) {
                val icon = attributes["icon"]!!.split(":")[1]
                IconicsDrawable(baseContext, "cmd-$icon").icon
            } else {
                when (entity.entityId.split(".")[0]) {
                    "input_boolean", "switch" -> CommunityMaterial.Icon2.cmd_light_switch
                    "light" -> CommunityMaterial.Icon2.cmd_lightbulb
                    "script" -> CommunityMaterial.Icon3.cmd_script_text_outline
                    "scene" -> CommunityMaterial.Icon3.cmd_palette_outline
                    else -> CommunityMaterial.Icon.cmd_cellphone
                }
            }

        if (entity.entityId.split(".")[0] in HomePresenterImpl.toggleDomains) {
            ToggleChip(
                checked = entity.state == "on",
                onCheckedChange = {
                    presenter.onEntityClicked(entity)
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
                    presenter.onEntityClicked(entity)
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

    private fun updateEntities() {
        mainScope.launch {
            entityViewModel.entitiesResponse = presenter.getEntities()
            delay(5000L)
            entityViewModel.entitiesResponse = presenter.getEntities()
        }
    }
}
