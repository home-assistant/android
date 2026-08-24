package io.homeassistant.companion.android.changelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.changelog.ChangelogAction
import io.homeassistant.companion.android.changelog.ChangelogEntry
import io.homeassistant.companion.android.changelog.ChangelogPlatform
import io.homeassistant.companion.android.changelog.ChangelogSection
import io.homeassistant.companion.android.changelog.ChangelogUiState
import io.homeassistant.companion.android.changelog.ChangelogViewModel
import io.homeassistant.companion.android.changelog.currentChangelog
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALabel
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.composable.HAVerticalDivider
import io.homeassistant.companion.android.common.compose.composable.LabelSize
import io.homeassistant.companion.android.common.compose.composable.LabelVariant
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

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
            ChangelogHeader(
                versionName = uiState.versionName,
                releaseUrl = uiState.releaseUrl,
                onActionClick = onActionClick,
            )
            uiState.sections.forEach { section ->
                ChangelogSectionContent(
                    section = section,
                    currentPlatform = uiState.currentPlatform,
                    onActionClick = onActionClick,
                )
            }
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
private fun ColumnScope.ChangelogHeader(
    versionName: String,
    releaseUrl: String,
    onActionClick: (ChangelogAction) -> Unit,
) {
    Text(
        text = versionName,
        style = HATextStyle.Headline.copy(textAlign = TextAlign.Start),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HADimens.SPACE4),
    )

    val linkStyles = HATextStyle.Link
    val subtitle = buildAnnotatedString {
        append(stringResource(commonR.string.changelog_subtitle_platforms))
        append(" · ")
        withLink(
            LinkAnnotation.Url(
                url = releaseUrl,
                styles = linkStyles,
                linkInteractionListener = { onActionClick(ChangelogAction.OpenUrl(releaseUrl)) },
            ),
        ) {
            append(stringResource(commonR.string.changelog_release_notes))
        }
    }
    Text(
        text = subtitle,
        style = HATextStyle.UserInput,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.ChangelogSectionContent(
    section: ChangelogSection,
    currentPlatform: ChangelogPlatform,
    onActionClick: (ChangelogAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        modifier = Modifier.padding(top = HADimens.SPACE4),
    ) {
        Box(
            modifier = Modifier
                .size(HASize.X2S)
                .background(color = section.category.markerColor(LocalHAColorScheme.current), shape = CircleShape),
        )
        Text(
            text = stringResource(section.category.labelRes).uppercase(),
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        // Bound the height to the entries so the divider can fill it
        modifier = Modifier.height(IntrinsicSize.Min),
    ) {
        // As wide as the section marker above, so the divider is centered under it and the
        // entries align with the section label
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(HASize.X2S)
                .fillMaxHeight(),
        ) {
            HAVerticalDivider(modifier = Modifier.fillMaxHeight())
        }
        Column {
            section.entries.forEach { entry ->
                ChangelogEntryContent(
                    entry = entry,
                    currentPlatform = currentPlatform,
                    onActionClick = onActionClick,
                )
            }
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
