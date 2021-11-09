package io.homeassistant.companion.android.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.home.HomePresenter
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.SetTitle
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.saveFavorites
import io.homeassistant.companion.android.viewModels.EntityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

@Composable
fun ScreenSettings(swipeDismissableNavController: NavHostController, entityViewModel: EntityViewModel, presenter: HomePresenter) {
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
                    HomeActivity.SCREEN_SET_FAVORITES
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
                saveFavorites(entityViewModel.favoriteEntities.toMutableSet(), presenter, mainScope)
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
fun ScreenSetFavorites(
    validEntities: List<Entity<Any>>,
    entityViewModel: EntityViewModel,
    context: Context,
    presenter: HomePresenter
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
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
        items(validEntities.size) { index ->
            val attributes = validEntities[index].attributes as Map<String, String>
            val iconBitmap = getIcon(attributes["icon"], validEntities[index].entityId.split(".")[0], context)
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
                    saveFavorites(entityViewModel.favoriteEntities.toMutableSet(), presenter, mainScope)
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
