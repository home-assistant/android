package io.homeassistant.companion.android.changelog.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ChevronRight
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.changelog.ChangelogAction
import io.homeassistant.companion.android.changelog.ChangelogEntry
import io.homeassistant.companion.android.changelog.ChangelogPlatform
import io.homeassistant.companion.android.changelog.ChangelogSection
import io.homeassistant.companion.android.changelog.ChangelogUiState
import io.homeassistant.companion.android.changelog.ChangelogViewModel
import io.homeassistant.companion.android.changelog.currentChangelog
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALabel
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.composable.HAVerticalDivider
import io.homeassistant.companion.android.common.compose.composable.LabelSize
import io.homeassistant.companion.android.common.compose.composable.LabelVariant
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import kotlinx.coroutines.launch

/**
 * Displays what changed in the current app version, for the app itself and its Wear OS and
 * Automotive companions.
 */
@Composable
internal fun ChangelogScreen(
    viewModel: ChangelogViewModel,
    onCloseClick: () -> Unit,
    onActionClick: (ChangelogAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChangelogScreenContent(
        uiState = uiState,
        onCloseClick = onCloseClick,
        onActionClick = onActionClick,
        modifier = modifier,
    )
}

@Composable
internal fun ChangelogScreenContent(
    uiState: ChangelogUiState,
    onCloseClick: () -> Unit,
    onActionClick: (ChangelogAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.changelog_screen_title)) },
                onCloseClick = onCloseClick,
            )
        },
    ) { contentPadding ->
        ChangelogContent(
            uiState = uiState,
            onGotItClick = onCloseClick,
            onActionClick = onActionClick,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

/**
 * The changelog content with its "Got it" button, without any top bar, so it can also be hosted
 * where a toolbar already exists (like the settings).
 */
@Composable
internal fun ChangelogContent(
    uiState: ChangelogUiState,
    onGotItClick: () -> Unit,
    onActionClick: (ChangelogAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HADimens.SPACE4),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            Text(
                text = uiState.versionName,
                style = HATextStyle.Headline.copy(textAlign = TextAlign.Start),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HADimens.SPACE4),
            )
            uiState.sections.forEach { section ->
                ChangelogSectionContent(
                    section = section,
                    currentPlatform = uiState.currentPlatform,
                    onActionClick = onActionClick,
                )
            }
            ShowFullChangelog(
                releaseUrl = uiState.releaseUrl,
                onActionClick = onActionClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        HAAccentButton(
            text = stringResource(commonR.string.changelog_got_it),
            onClick = onGotItClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HADimens.SPACE4)
                .align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ShowFullChangelog(
    releaseUrl: String,
    onActionClick: (ChangelogAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    HAPlainButton(
        text = stringResource(commonR.string.changelog_show_full_changelog),
        size = ButtonSize.SMALL,
        onClick = { onActionClick(ChangelogAction.OpenUrl(releaseUrl)) },
        onLongClickLabel = stringResource(commonR.string.changelog_copy_release_url),
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(releaseUrl, releaseUrl)))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ColumnScope.ChangelogSectionContent(
    section: ChangelogSection,
    currentPlatform: ChangelogPlatform,
    onActionClick: (ChangelogAction) -> Unit,
) {
    val sectionMarkerSize = HASize.X2S
    val sectionMarkerSpacing = HADimens.SPACE2

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sectionMarkerSpacing),
        modifier = Modifier.padding(top = HADimens.SPACE4),
    ) {
        Box(
            modifier = Modifier
                .size(sectionMarkerSize)
                .background(color = section.category.markerColor(LocalHAColorScheme.current), shape = CircleShape),
        )
        Text(
            text = stringResource(section.category.labelRes),
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        )
    }

    // A Box instead of a Row with an intrinsic height: intrinsic measurements underestimate the
    // height of wrapping content (multi line text, flowing tags) on some widths and font metrics,
    // cutting the end of the last entry. The divider overlays the entries and matches their
    // height exactly instead.
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Aligns the entries with the section label above
                .padding(start = sectionMarkerSize + sectionMarkerSpacing),
        ) {
            section.entries.forEach { entry ->
                ChangelogEntryContent(
                    entry = entry,
                    currentPlatform = currentPlatform,
                    onActionClick = onActionClick,
                )
            }
        }
        // As wide as the section marker above, so the divider is centered under it
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.Start)
                .width(sectionMarkerSize),
        ) {
            HAVerticalDivider(modifier = Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun ChangelogEntryContent(
    entry: ChangelogEntry,
    currentPlatform: ChangelogPlatform,
    onActionClick: (ChangelogAction) -> Unit,
) {
    val action = entry.action
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (action != null) {
                    Modifier.clickable(
                        onClickLabel = stringResource(commonR.string.changelog_entry_open),
                        role = Role.Button,
                    ) { onActionClick(action) }
                } else {
                    Modifier
                },
            )
            .padding(vertical = HADimens.SPACE2),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            val contentHtml = stringResource(entry.contentRes)
            val content = remember(contentHtml) { AnnotatedString.fromHtml(contentHtml) }
            Text(
                text = content,
                style = HATextStyle.Body.copy(
                    color = LocalHAColorScheme.current.colorTextPrimary,
                    textAlign = TextAlign.Start,
                ),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
            ) {
                ChangelogPlatform.entries.filter { it in entry.platforms }.forEach { platform ->
                    PlatformTag(platform = platform, isCurrent = platform == currentPlatform)
                }
            }
        }
        if (action != null) {
            Icon(
                imageVector = Mdi.ChevronRight.rememberImageVector(autoMirror = true),
                contentDescription = null,
                tint = LocalHAColorScheme.current.colorOnNeutralQuiet,
            )
        }
    }
}

@Composable
private fun PlatformTag(platform: ChangelogPlatform, isCurrent: Boolean) {
    val label = stringResource(platform.labelRes)
    HALabel(
        text = if (isCurrent) {
            stringResource(commonR.string.changelog_platform_this_device, label)
        } else {
            label
        },
        variant = if (isCurrent) LabelVariant.PRIMARY else LabelVariant.NEUTRAL,
        size = LabelSize.SMALL,
    )
}

@Preview
@Composable
private fun ChangelogScreenPreview() {
    HAThemeForPreview {
        ChangelogScreenContent(
            uiState = ChangelogUiState(
                versionName = "2026.7.6",
                releaseUrl = "https://github.com/home-assistant/android/releases/tag/2026.7.6",
                currentPlatform = ChangelogPlatform.APP,
                sections = currentChangelog.toSections(),
            ),
            onCloseClick = {},
            onActionClick = {},
        )
    }
}
