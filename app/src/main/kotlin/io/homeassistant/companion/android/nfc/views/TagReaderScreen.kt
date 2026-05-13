package io.homeassistant.companion.android.nfc.views

import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.util.adaptiveIconPainterResource
import io.homeassistant.companion.android.nfc.TagReaderUiState
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlinx.coroutines.launch

private val SCANNING_CONTENT_HEIGHT = 160.dp

/** Visible diameter of the sheet close button (background + clip). */
private val CLOSE_BUTTON_VISIBLE_SIZE = 40.dp

/** Tap target size of the sheet close button — meets Material's 48dp minimum for accessibility. */
private val CLOSE_BUTTON_TAP_SIZE = 48.dp

/**
 * Renders the tag reader UI for a given [state].
 *
 * - [TagReaderUiState.Initial]: nothing is rendered.
 * - [TagReaderUiState.ApprovingTag]: bottom sheet with the approval content. Dismissal is
 *   wired to [onDismissed].
 * - [TagReaderUiState.Scanning]: bottom sheet with the loading content.
 * - [TagReaderUiState.Error]: a snackbar is shown for [TagReaderUiState.Error.messageRes];
 *   once it is dismissed [onErrorAcknowledged] is invoked.
 * - [TagReaderUiState.Done]: if a sheet was visible, it is animated out before [onFinished]
 *   is invoked. Without this lag the Scanning → Done transition would snap away unanimated.
 *
 * @param onFinished invoked after the screen has finished any closing animations and the
 * activity should call `finish()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagReaderScreen(
    state: TagReaderUiState,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
    onDismissed: () -> Unit,
    onErrorAcknowledged: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberHAModalBottomSheetState()

    // Tracks whether the bottom sheet has ever been shown. Used to decide whether the
    // [TagReaderUiState.Done] state should keep the sheet in composition long enough to
    // animate it out without this flag, an Error → Done transition would briefly render
    // the sheet only to immediately animate it away (a visible blink).
    var sheetWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is TagReaderUiState.ApprovingTag || state is TagReaderUiState.Scanning) {
            sheetWasShown = true
        }
    }

    val showSheet = state is TagReaderUiState.ApprovingTag ||
        state is TagReaderUiState.Scanning ||
        (state is TagReaderUiState.Done && sheetWasShown)

    Box(modifier = modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(safeBottomWindowInsets(applyHorizontal = true)),
        )

        if (showSheet) {
            HAModalBottomSheet(
                bottomSheetState = sheetState,
                onDismissRequest = onDismissed,
                dragHandle = {},
            ) {
                Column(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                ) {
                    TagApprovalSheetHeader(onClose = onDismissed)

                    when (state) {
                        is TagReaderUiState.ApprovingTag -> ApprovingContent(
                            tagId = state.tagId,
                            onAllowOnce = onAllowOnce,
                            onAllowAlways = onAllowAlways,
                        )

                        TagReaderUiState.Scanning -> ScanningContent()
                        TagReaderUiState.Done -> Unit
                    }
                }
            }
        }
    }

    if (state is TagReaderUiState.Done) {
        LaunchedEffect(Unit) {
            if (sheetState.isVisible) {
                sheetState.hide()
            }
            onFinished()
        }
    }

    if (state is TagReaderUiState.Error) {
        val message = stringResource(state.messageRes)
        LaunchedEffect(state) {
            snackbarHostState.showSnackbar(message)
            onErrorAcknowledged()
        }
    }
}

@Composable
private fun TagApprovalSheetHeader(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = HADimens.SPACE2),
    ) {
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            Image(
                painter = adaptiveIconPainterResource(R.mipmap.ic_launcher_round),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = HADimens.SPACE5)
                    .size(36.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape),
            )
            Text(
                text = stringResource(commonR.string.app_name),
                style = HATextStyle.BodyMedium,
                modifier = Modifier
                    .padding(HADimens.SPACE2)
                    .align(Alignment.CenterHorizontally),
            )
        }
        val cancelContentDescription = stringResource(commonR.string.cancel)
        // Replicates Close button of the Android 17 biometrics bottom sheet
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(HADimens.SPACE2)
                .size(CLOSE_BUTTON_TAP_SIZE)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                )
                .semantics {
                    contentDescription = cancelContentDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(CLOSE_BUTTON_VISIBLE_SIZE)
                    .clip(CircleShape)
                    .background(LocalHAColorScheme.current.colorSurfaceLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = LocalHAColorScheme.current.colorOnNeutralQuiet,
                )
            }
        }
    }
}

@Composable
private fun ApprovingContent(tagId: String, onAllowOnce: () -> Unit, onAllowAlways: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HADimens.SPACE6, vertical = HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(commonR.string.tag_approval_title),
            style = HATextStyle.HeadlineMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(commonR.string.tag_approval_description),
            style = HATextStyle.Body,
            modifier = Modifier
                .fillMaxWidth(),
        )

        TagBanner(tagId, modifier = Modifier.padding(bottom = HADimens.SPACE4))

        HAFilledButton(
            text = stringResource(commonR.string.tag_allow_once),
            onClick = onAllowOnce,
            modifier = Modifier.fillMaxWidth(),
        )

        HAPlainButton(
            text = stringResource(commonR.string.tag_allow_always),
            onClick = onAllowAlways,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TagBanner(tagId: String, modifier: Modifier = Modifier) {
    val copyContentDescription = stringResource(commonR.string.tag_copy_id)
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    HABanner(
        modifier = modifier,
        contentPadding = PaddingValues(start = HADimens.SPACE3) + PaddingValues(vertical = HADimens.SPACE3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(commonR.string.tag_id),
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                modifier = Modifier.padding(bottom = HADimens.SPACE1),
            )
            Text(
                text = tagId,
                style = HATextStyle.Body.copy(
                    textAlign = TextAlign.Start,
                    color = LocalHAColorScheme.current.colorTextPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(tagId, tagId)))
                }
            },
            modifier = Modifier.semantics {
                contentDescription = copyContentDescription
            },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                tint = LocalHAColorScheme.current.colorOnNeutralQuiet,
            )
        }
    }
}

@Composable
private fun ScanningContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCANNING_CONTENT_HEIGHT)
            .padding(horizontal = HADimens.SPACE6, vertical = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(commonR.string.tag_reader_title),
            style = HATextStyle.Body,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun ApprovingPreview() {
    HAThemeForPreview {
        TagReaderScreen(
            state = TagReaderUiState.ApprovingTag("aa69fa33-39fd-4247-a559-af06081b2935"),
            onAllowOnce = {},
            onErrorAcknowledged = {},
            onDismissed = {},
            onAllowAlways = {},
            onFinished = {},
        )
    }
}

@Preview
@Composable
private fun ScanningPreview() {
    HAThemeForPreview {
        TagReaderScreen(
            state = TagReaderUiState.Scanning,
            onAllowOnce = {},
            onErrorAcknowledged = {},
            onDismissed = {},
            onAllowAlways = {},
            onFinished = {},
        )
    }
}
