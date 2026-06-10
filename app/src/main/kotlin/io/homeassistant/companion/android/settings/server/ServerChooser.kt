package io.homeassistant.companion.android.settings.server

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

private val ROW_MIN_HEIGHT = HADimens.SPACE20
private val AVATAR_SIZE = HADimens.SPACE12
private val AVATAR_BADGE_SIZE = HADimens.SPACE5
private val AVATAR_BADGE_ICON_SIZE = HADimens.SPACE3

// Inset the in-between dividers so they start where the text starts, aligned past the avatar.
private val DIVIDER_START_INSET = HADimens.SPACE4 + AVATAR_SIZE + HADimens.SPACE3

/**
 * Bottom sheet that lets the user pick one of their registered servers, showing the current user's
 * profile picture and name for each one.
 *
 * The sheet provides its own scrim and surface through [HAModalBottomSheet], so it must be hosted
 * directly in a Compose hierarchy (not inside a `BottomSheetDialogFragment`).
 *
 * @param items the servers to choose from, already resolved by [ServerChooserItemsManager].
 * @param onServerSelected invoked with [ServerChooserItem.serverId] when a row is tapped.
 * @param onDismissRequest invoked when the user dismisses the sheet (scrim tap, swipe or back).
 * @param sheetState state controlling the sheet, exposed so the host can drive show/hide animations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerChooser(
    items: List<ServerChooserItem>,
    onServerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    sheetState: SheetState = rememberHAModalBottomSheetState(),
) {
    HAModalBottomSheet(
        bottomSheetState = sheetState,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        LazyColumn {
            item {
                Text(
                    text = stringResource(commonR.string.server_select),
                    style = HATextStyle.HeadlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
                )
            }

            itemsIndexed(items, key = { _, item -> item.serverId }) { index, item ->
                ServerChooserRow(item = item, onServerSelected = onServerSelected)
                if (index < items.lastIndex) {
                    HAHorizontalDivider(modifier = Modifier.padding(start = DIVIDER_START_INSET, end = HADimens.SPACE4))
                }
            }
            item {
                Spacer(
                    modifier = Modifier
                        .height(HADimens.SPACE6)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                )
            }
        }
    }
}

@Composable
private fun ServerChooserRow(item: ServerChooserItem, onServerSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .clickable { onServerSelected(item.serverId) }
            .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE2),
    ) {
        ServerUserAvatar(item = item)
        Spacer(modifier = Modifier.width(HADimens.SPACE3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.serverName,
                style = HATextStyle.Body.copy(
                    color = LocalHAColorScheme.current.colorTextPrimary,
                    textAlign = TextAlign.Start,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.serverName != item.userName) {
                Text(
                    text = item.userName,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ServerUserAvatar(item: ServerChooserItem, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(AVATAR_SIZE)) {
        val avatarModifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)

        val avatar = item.userAvatar
        if (avatar != null) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = avatarModifier,
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = avatarModifier.background(LocalHAColorScheme.current.colorFillPrimaryNormalResting),
            ) {
                Text(
                    text = item.initials,
                    style = HATextStyle.BodyMedium.copy(color = LocalHAColorScheme.current.colorOnPrimaryNormal),
                )
            }
        }

        if (item.isActive) {
            ActiveBadge(modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * A small check badge overlaid on the bottom-end of an avatar to mark the active server. The ring
 * in the sheet's surface color makes it stand off the avatar edge, like a status badge.
 */
@Composable
private fun ActiveBadge(modifier: Modifier = Modifier) {
    val colorScheme = LocalHAColorScheme.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AVATAR_BADGE_SIZE)
            .clip(CircleShape)
            .background(colorScheme.colorSurfaceDefault)
            .padding(2.dp)
            .clip(CircleShape)
            .background(colorScheme.colorFillPrimaryLoudResting),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(commonR.string.server_active),
            tint = colorScheme.colorOnPrimaryLoud,
            modifier = Modifier.size(AVATAR_BADGE_ICON_SIZE),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ServerChooserPreview() {
    HAThemeForPreview {
        ServerChooser(
            items = listOf(
                ServerChooserItem(serverId = 1, userName = "Alice Smith", serverName = "Home", isActive = true),
                ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Friends home"),
            ),
            onServerSelected = {},
        )
    }
}
