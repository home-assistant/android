package io.homeassistant.companion.android.frontend.error

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
import androidx.compose.runtime.rememberCoroutineScope
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
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HADetails
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HATopBarPlaceholder
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.onboarding.connection.ConnectivityChecksSection
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.coroutines.launch

private val MaxContentWidth = MaxButtonWidth

@VisibleForTesting
internal const val URL_INFO_TAG = "url_info"

private const val URL_DOCUMENTATION = "https://companion.home-assistant.io/docs/troubleshooting/faqs/"
private const val URL_COMMUNITY_FORUM = "https://community.home-assistant.io/c/mobile-apps/android-companion/42"
private const val URL_GITHUB_ISSUES = "https://github.com/home-assistant/android/issues"
private const val URL_DISCORD = "https://discord.com/channels/330944238910963714/1284965926336335993"

@Composable
fun FrontendConnectionErrorScreen(
    stateProvider: FrontendConnectionErrorStateProvider,
    onOpenExternalLink: suspend (Uri) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val url by stateProvider.urlFlow.collectAsState()
    val error by stateProvider.errorFlow.collectAsState()
    val connectivityCheckState by stateProvider.connectivityCheckState.collectAsState()

    FrontendConnectionErrorScreen(
        url = url,
        error = error,
        onOpenExternalLink = onOpenExternalLink,
        modifier = modifier,
        connectivityCheckState = connectivityCheckState,
        onRetryConnectivityCheck = stateProvider::runConnectivityChecks,
        actions = actions,
    )
}

/**
 * Connection error screen that displays a [FrontendConnectionError].
 *
 * Maps the error type to an appropriate icon and extracts error details.
 *
 * @param url The URL that failed to connect
 * @param error The connection error to display, or null to show nothing
 * @param onOpenExternalLink Callback when an external link should be opened
 * @param connectivityCheckState The current connectivity check state
 * @param modifier Modifier for the screen
 * @param errorDetailsExpanded Whether the error details section should be expanded by default
 * @param onRetryConnectivityCheck Callback when retry connectivity check is requested
 * @param actions Composable slot for action buttons at the bottom of the screen
 */
@Composable
fun FrontendConnectionErrorScreen(
    url: String?,
    error: FrontendConnectionError?,
    onOpenExternalLink: suspend (Uri) -> Unit,
    connectivityCheckState: ConnectivityCheckState,
    modifier: Modifier = Modifier,
    errorDetailsExpanded: Boolean = false,
    onRetryConnectivityCheck: () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    error?.let { connectionError ->
        val icon = when (connectionError) {
            is FrontendConnectionError.AuthenticationError -> R.drawable.ic_casita_crying
            is FrontendConnectionError.UnknownError -> R.drawable.ic_casita_problem
            is FrontendConnectionError.UnreachableError -> R.drawable.ic_casita_no_connection
        }

        FrontendConnectionErrorScreen(
            onOpenExternalLink = onOpenExternalLink,
            icon = ImageVector.vectorResource(icon),
            title = stringResource(connectionError.title),
            subtitle = stringResource(connectionError.message),
            url = url,
            connectivityCheckState = connectivityCheckState,
            onRetryConnectivityCheck = onRetryConnectivityCheck,
            errorDescription = connectionError.errorDetails
                ?: stringResource(commonR.string.no_description),
            errorType = connectionError.rawErrorType,
            errorDetailsExpanded = errorDetailsExpanded,
            modifier = modifier,
            actions = actions,
        )
    }
}

/**
 * Pure UI connection error screen with all parameters explicitly provided.
 *
 * @param onOpenExternalLink Callback when an external link should be opened
 * @param icon The icon to display at the top of the screen
 * @param title The error title
 * @param subtitle The error message/subtitle
 * @param url The URL that failed to connect
 * @param connectivityCheckState The current connectivity check state
 * @param onRetryConnectivityCheck Callback when retry connectivity check is requested
 * @param errorDescription Detailed error description
 * @param errorType The raw error type for debugging
 * @param errorDetailsExpanded Whether the error details section should be expanded by default
 * @param modifier Modifier for the screen
 * @param actions Composable slot for action buttons at the bottom of the screen
 */
@Composable
fun FrontendConnectionErrorScreen(
    onOpenExternalLink: suspend (Uri) -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
    url: String?,
    connectivityCheckState: ConnectivityCheckState,
    onRetryConnectivityCheck: () -> Unit,
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
        FrontendErrorContent(
            onOpenExternalLink = onOpenExternalLink,
            title = title,
            subtitle = subtitle,
            errorDescription = errorDescription,
            errorType = errorType,
            url = url,
            icon = icon,
            errorDetailsExpanded = errorDetailsExpanded,
            connectivityCheckState = connectivityCheckState,
            onRetryConnectivityCheck = onRetryConnectivityCheck,
            actions = actions,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun FrontendErrorContent(
    onOpenExternalLink: suspend (Uri) -> Unit,
    title: String,
    subtitle: String,
    errorDescription: String,
    errorType: String,
    url: String?,
    icon: ImageVector,
    errorDetailsExpanded: Boolean,
    connectivityCheckState: ConnectivityCheckState,
    onRetryConnectivityCheck: () -> Unit,
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

        ErrorDetails(
            errorDescription = errorDescription,
            errorType = errorType,
            expanded = errorDetailsExpanded,
            connectivityCheckState = connectivityCheckState,
            onRetryConnectivityCheck = onRetryConnectivityCheck,
        )

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
private fun UrlInfo(url: String?, onOpenExternalLink: suspend (Uri) -> Unit) {
    url?.let { url ->
        HABanner(
            modifier = Modifier
                .width(MaxContentWidth)
                .testTag(URL_INFO_TAG),
        ) {
            val coroutineContext = rememberCoroutineScope()
            val annotatedString = buildAnnotatedString {
                append(stringResource(commonR.string.connection_error_url_info))
                appendLine()
                withLink(
                    LinkAnnotation.Url(
                        url,
                        styles = HATextStyle.Link,
                        linkInteractionListener = {
                            coroutineContext.launch {
                                onOpenExternalLink(url.toUri())
                            }
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
private fun ErrorDetails(
    errorDescription: String,
    errorType: String,
    expanded: Boolean,
    connectivityCheckState: ConnectivityCheckState,
    onRetryConnectivityCheck: () -> Unit,
) {
    HADetails(
        stringResource(commonR.string.connection_error_more_details),
        defaultExpanded = expanded,
        modifier = Modifier.width(MaxContentWidth),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
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

            ConnectivityChecksSection(
                connectivityCheckState = connectivityCheckState,
                onRetryConnectivityCheck = onRetryConnectivityCheck,
            )
        }
    }
}

@Composable
private fun ColumnScope.GetMoreHelp(onOpenExternalLink: suspend (Uri) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    Text(stringResource(commonR.string.connection_error_help), style = HATextStyle.Body)

    Row {
        HAIconButton(
            icon = Icons.Outlined.Newspaper,
            contentDescription = stringResource(commonR.string.connection_error_documentation_content_description),
            onClick = {
                coroutineScope.launch {
                    onOpenExternalLink(URL_DOCUMENTATION.toUri())
                }
            },
        )
        HAIconButton(
            icon = Icons.Outlined.Forum,
            contentDescription = stringResource(commonR.string.connection_error_forum_content_description),
            onClick = {
                coroutineScope.launch {
                    onOpenExternalLink(URL_COMMUNITY_FORUM.toUri())
                }
            },
        )
        HAIconButton(
            icon = ImageVector.vectorResource(R.drawable.github),
            contentDescription = stringResource(commonR.string.connection_error_github_content_description),
            onClick = {
                coroutineScope.launch {
                    onOpenExternalLink(URL_GITHUB_ISSUES.toUri())
                }
            },
        )
        HAIconButton(
            icon = ImageVector.vectorResource(R.drawable.discord),
            contentDescription = stringResource(commonR.string.connection_error_discord_content_description),
            onClick = {
                coroutineScope.launch {
                    onOpenExternalLink(URL_DISCORD.toUri())
                }
            },
        )
    }
}

@HAPreviews
@Composable
private fun FrontendErrorScreenPreview() {
    HAThemeForPreview {
        FrontendConnectionErrorScreen(
            onOpenExternalLink = {},
            title = "Connection failed",
            subtitle = "Unable to reach your Home Assistant server",
            errorType = "UnreachableError",
            connectivityCheckState = ConnectivityCheckState(),
            onRetryConnectivityCheck = {},
            errorDescription = "Connection timed out after 30 seconds",
            url = "http://homeassistant.local:8123",
            icon = ImageVector.vectorResource(R.drawable.ic_casita_no_connection),
            errorDetailsExpanded = true,
        )
    }
}
