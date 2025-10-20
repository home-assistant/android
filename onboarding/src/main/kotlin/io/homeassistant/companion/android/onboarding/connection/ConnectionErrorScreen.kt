package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HADetails
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.R

private val MaxContentWidth = MaxButtonWidth

@VisibleForTesting
internal const val URL_INFO_TAG = "url_info"

@Composable
internal fun ConnectionErrorScreen(
    viewModel: ConnectionViewModel,
    onOpenExternalLink: (Uri) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url by viewModel.urlFlow.collectAsState()
    val error by viewModel.errorFlow.collectAsState()

    ConnectionErrorScreen(
        url = url,
        error = error,
        onOpenExternalLink = onOpenExternalLink,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
internal fun ConnectionErrorScreen(
    url: String?,
    error: ConnectionError?,
    onOpenExternalLink: (Uri) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
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
            errorDescription = error.errorDetails,
            errorType = error.rawErrorType,
            modifier = modifier,
        ) {
            CloseAction { onBackClick() }
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
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
    ) { contentPadding ->
        ConnectionErrorContent(
            onOpenExternalLink = onOpenExternalLink,
            title = title,
            subtitle = subtitle,
            errorDescription = errorDescription,
            errorType = errorType,
            url = url,
            icon = icon,
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
        Spacer(modifier = Modifier.height(64.dp - HADimens.SPACE6))

        Header(icon, title = title, subtitle = subtitle)

        UrlInfo(url, onOpenExternalLink = onOpenExternalLink)

        ErrorDetails(errorDescription = errorDescription, errorType = errorType)

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
    url?.let {
        HABanner(
            modifier = Modifier
                .width(MaxContentWidth)
                .testTag(URL_INFO_TAG),
        ) {
            val annotatedString = buildAnnotatedString {
                append(stringResource(R.string.connection_error_url_info))
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

            Text(
                text = annotatedString,
                style = HATextStyle.BodyMedium,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ErrorDetails(errorDescription: String, errorType: String) {
    HADetails(
        stringResource(R.string.connection_error_more_details),
        modifier = Modifier.width(MaxContentWidth),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
            Text(
                text = stringResource(R.string.connection_error_more_details_description),
                style = HATextStyle.Body,
            )
            Text(
                text = errorDescription,
                style = HATextStyle.BodyMedium.copy(
                    textAlign = TextAlign.Start,
                ),
            )
            Text(
                text = stringResource(R.string.connection_error_more_details_error),
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

@Composable
private fun ColumnScope.GetMoreHelp(onOpenExternalLink: (Uri) -> Unit) {
    Text(stringResource(R.string.connection_error_help), style = HATextStyle.Body)

    Row {
        HAIconButton(
            icon = Icons.Outlined.Newspaper,
            contentDescription = stringResource(R.string.connection_error_documentation_content_description),
            onClick = {
                onOpenExternalLink("https://companion.home-assistant.io/docs/troubleshooting/faqs/".toUri())
            },
        )
        HAIconButton(
            icon = ImageVector.vectorResource(R.drawable.github),
            contentDescription = "Home Assistant Github",
            onClick = {
                onOpenExternalLink("https://github.com/home-assistant/android/".toUri())
            },
        )
        HAIconButton(
            icon = ImageVector.vectorResource(R.drawable.discord),
            contentDescription = "Home Assistant Discord",
            onClick = {
                onOpenExternalLink("https://discord.com/channels/330944238910963714/1284965926336335993".toUri())
            },
        )
    }
}

@Composable
private fun CloseAction(onClick: () -> Unit) {
    HAAccentButton(
        stringResource(R.string.back),
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
            actions = {
                CloseAction { }
            },
        )
    }
}
