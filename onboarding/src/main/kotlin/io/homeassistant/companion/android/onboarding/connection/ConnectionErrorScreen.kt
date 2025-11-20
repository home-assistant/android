package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HADetails
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBarPlaceholder
import io.homeassistant.companion.android.onboarding.R

private val MaxContentWidth = MaxButtonWidth

@VisibleForTesting
internal const val URL_INFO_TAG = "url_info"

private const val URL_DOCUMENTATION = "https://companion.home-assistant.io/docs/troubleshooting/faqs/"
private const val URL_COMMUNITY_FORUM = "https://community.home-assistant.io/c/mobile-apps/android-companion/42"
private const val URL_GITHUB_ISSUES = "https://github.com/home-assistant/android/issues"
private const val URL_DISCORD = "https://discord.com/channels/330944238910963714/1284965926336335993"

@Composable
internal fun ConnectionErrorScreen(
    viewModel: ConnectionViewModel,
    onOpenExternalLink: (Uri) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url by viewModel.urlFlow.collectAsState()
    val error by viewModel.errorFlow.collectAsState()

    ConnectionErrorScreen(
        url = url,
        error = error,
        onOpenExternalLink = onOpenExternalLink,
        onCloseClick = onCloseClick,
        modifier = modifier,
    )
}

@Composable
internal fun ConnectionErrorScreen(
    url: String?,
    error: ConnectionError?,
    onOpenExternalLink: (Uri) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorDetailsExpanded: Boolean = false,
) {
    error?.let { error ->
        val icon = when (error) {
            is ConnectionError.AuthenticationError -> R.drawable.ic_casita_crying
            is ConnectionError.UnknownError -> R.drawable.ic_casita_problem
            is ConnectionError.UnreachableError -> R.drawable.ic_casita_no_connection
        }

        ConnectionErrorScreen(
            onOpenExternalLink = onOpenExternalLink,
            icon = ImageVector.vectorResource(icon),
            title = stringResource(error.title),
            subtitle = stringResource(error.message),
            url = url,
            errorDescription = error.errorDetails ?: stringResource(commonR.string.no_description),
            errorType = error.rawErrorType,
            errorDetailsExpanded = errorDetailsExpanded,
            modifier = modifier,
        ) {
            CloseAction { onCloseClick() }
        }
    }
}

@Composable
internal fun ConnectionErrorScreen(
    onOpenExternalLink: (Uri) -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
    url: String?,
    errorDescription: String,
    errorType: String,
    errorDetailsExpanded: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        ConnectionErrorContent(
            onOpenExternalLink = onOpenExternalLink,
            title = title,
            subtitle = subtitle,
            errorDescription = errorDescription,
            errorType = errorType,
            url = url,
            icon = icon,
            errorDetailsExpanded = errorDetailsExpanded,
            actions = actions,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun ConnectionErrorContent(
    onOpenExternalLink: (Uri) -> Unit,
    title: String,
    subtitle: String,
    errorDescription: String,
    errorType: String,
    url: String?,
    icon: ImageVector,
    errorDetailsExpanded: Boolean,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        // Fake Space at the top since we don't have a topbar on this screen.
        // It makes the image bellow perfectly aligned with the other screens.
        HATopBarPlaceholder()

        Header(icon, title = title, subtitle = subtitle)

        UrlInfo(url, onOpenExternalLink = onOpenExternalLink)

        ErrorDetails(errorDescription = errorDescription, errorType = errorType, expanded = errorDetailsExpanded)

        GetMoreHelp(onOpenExternalLink = onOpenExternalLink)

        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun ColumnScope.Header(icon: ImageVector, title: String, subtitle: String) {
    Image(
        modifier = Modifier
            // This padding and size are adjusted to have image
            // aligned with the one in the other onboarding screens
            .padding(top = HADimens.SPACE6)
            .padding(all = 20.dp)
            .size(120.dp),
        imageVector = icon,
        contentDescription = null,
    )

    Text(
        text = title,
        style = HATextStyle.Headline,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )

    Text(
        text = subtitle,
        style = HATextStyle.Body,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )
}

@Composable
private fun UrlInfo(url: String?, onOpenExternalLink: (Uri) -> Unit) {
    url?.let { url ->
        HABanner(
            modifier = Modifier
                .width(MaxContentWidth)
                .testTag(URL_INFO_TAG),
        ) {
            val annotatedString = buildAnnotatedString {
                append(stringResource(commonR.string.connection_error_url_info))
                appendLine()
                withLink(
                    LinkAnnotation.Url(
                        url,
                        styles = HATextStyle.Link,
                        linkInteractionListener = {
                            onOpenExternalLink(url.toUri())
                        },
                    ),
                ) {
                    append(url)
                }
            }

            SelectionContainer {
                Text(
                    text = annotatedString,
                    style = HATextStyle.BodyMedium,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ErrorDetails(errorDescription: String, errorType: String, expanded: Boolean) {
    HADetails(
        stringResource(commonR.string.connection_error_more_details),
        defaultExpanded = expanded,
        modifier = Modifier.width(MaxContentWidth),
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
                Text(
                    text = stringResource(commonR.string.connection_error_more_details_description),
                    style = HATextStyle.Body,
                )
                Text(
                    text = errorDescription,
                    style = HATextStyle.BodyMedium.copy(
                        textAlign = TextAlign.Start,
                    ),
                )
                Text(
                    text = stringResource(commonR.string.connection_error_more_details_error),
                    style = HATextStyle.Body,
                )
                Text(
                    text = errorType,
                    style = HATextStyle.BodyMedium.copy(
                        textAlign = TextAlign.Start,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.GetMoreHelp(onOpenExternalLink: (Uri) -> Unit) {
    Text(stringResource(commonR.string.connection_error_help), style = HATextStyle.Body)

    Row {
        HAIconButton(
            icon = Icons.Outlined.Newspaper,
            contentDescription = stringResource(commonR.string.connection_error_documentation_content_description),
            onClick = {
                onOpenExternalLink(URL_DOCUMENTATION.toUri())
            },
        )
        HAIconButton(
            icon = Icons.Outlined.Forum,
            contentDescription = stringResource(commonR.string.connection_error_forum_content_description),
            onClick = {
                onOpenExternalLink(URL_COMMUNITY_FORUM.toUri())
            },
        )
        HAIconButton(
            icon = ImageVector.vectorResource(R.drawable.github),
            contentDescription = stringResource(commonR.string.connection_error_github_content_description),
            onClick = {
                onOpenExternalLink(URL_GITHUB_ISSUES.toUri())
            },
        )
        HAIconButton(
            icon = ImageVector.vectorResource(R.drawable.discord),
            contentDescription = stringResource(commonR.string.connection_error_discord_content_description),
            onClick = {
                onOpenExternalLink(URL_DISCORD.toUri())
            },
        )
    }
}

@Composable
private fun CloseAction(onClick: () -> Unit) {
    HAAccentButton(
        stringResource(commonR.string.back),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = HADimens.SPACE6),
    )
}

@HAPreviews
@Composable
private fun ConnectionErrorScreenPreview() {
    HAThemeForPreview {
        ConnectionErrorScreen(
            onOpenExternalLink = {},
            title = "",
            subtitle = "",
            errorType = "",
            errorDescription = "",
            url = "http://ha.org",
            icon = ImageVector.vectorResource(R.drawable.ic_casita_no_connection),
            errorDetailsExpanded = true,
            actions = {
                CloseAction { }
            },
        )
    }
}
