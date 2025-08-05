package io.homeassistant.companion.android.webview.addto

import android.content.Context
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.todo.TodoWidgetConfigureActivity

sealed interface AddToAction {

    @Composable
    fun Icon(modifier: Modifier)

    @Composable
    fun text(): String

    fun action(context: Context, viewModel: AddToViewModel, entityId: String)

    data object AndroidAutoFavorite : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_car),
                contentDescription = null,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_android_auto_favorite)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            viewModel.addToAndroidAutoFavorite()
        }
    }

    data object Shortcut : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Icon(
                imageVector = ImageVector.vectorResource(commonR.drawable.ic_shortcut),
                contentDescription = null,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_shortcut)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            TODO("Not yet implemented")
        }
    }

    data object Tile : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Icon(
                imageVector = ImageVector.vectorResource(commonR.drawable.ic_tile),
                contentDescription = null,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_tile)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            // TODO go to a new tile
//                                    startActivity(SettingsActivity.newInstance(requireContext(),
//                                        SettingsActivity.Deeplink.QSTile()
//                                    ))
        }
    }

    data object EntityWidget : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Image(
                asset = CommunityMaterial.Icon3.cmd_shape,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_entity_widget)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            context.startActivity(
                EntityWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    data object MediaPlayerWidget : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Image(
                asset = CommunityMaterial.Icon3.cmd_play_box_multiple,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_media_player_widget)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            context.startActivity(
                MediaPlayerControlsWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    data object CameraWidget : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Image(
                asset = CommunityMaterial.Icon.cmd_camera_image,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_camera_widget)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            context.startActivity(
                CameraWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    data object TodoWidget : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Image(
                asset = CommunityMaterial.Icon.cmd_clipboard_list,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_todo_widget)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            context.startActivity(
                TodoWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    data class Watch(val name: String) : AddToAction {
        @Composable
        override fun Icon(modifier: Modifier) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_watch_24),
                contentDescription = null,
                modifier = modifier,
            )
        }

        @Composable
        override fun text(): String {
            return stringResource(commonR.string.add_to_watch_favorite, name)
        }

        override fun action(context: Context, viewModel: AddToViewModel, entityId: String) {
            TODO("Not yet implemented")
        }
    }
}
