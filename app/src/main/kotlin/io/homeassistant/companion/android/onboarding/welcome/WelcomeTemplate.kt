package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import kotlinx.coroutines.launch

private val ICON_SIZE = 120.dp

/**
 * Maximum width applied to the textual content of the welcome screens so it stays readable on
 * large screens. Shared so that any extra [WelcomeTemplate] content (such as a warning) can align
 * with the title and details.
 */
internal val WelcomeContentMaxWidth = MaxButtonWidth

/**
 * Shared layout for the onboarding welcome screens ([WelcomeScreen] and [WelcomeInvitationScreen]).
 *
 * Displays the Home Assistant branding, a [title] and [details], any optional [content] placed below
 * the details (for example a warning), and the bottom action buttons. The primary action is described
 * by [primaryButtonText]/[onPrimaryClick]; a "Learn more" button is always shown below it and runs
 * [onLearnMoreClick] in a coroutine scoped to this composable.
 *
 * @param content Optional extra content rendered below the details, within the scrolling column.
 */
@Composable
internal fun WelcomeTemplate(
    title: String,
    details: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    onLearnMoreClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        // We use spacer to position the image where we want when there is remaining space in the column using percentage
        val positionPercentage = 0.2f
        Spacer(modifier = Modifier.weight(positionPercentage))

        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = stringResource(commonR.string.home_assistant_branding_icon_content_description),
            modifier = Modifier.size(ICON_SIZE),
        )

        Text(
            text = title,
            style = HATextStyle.Headline,
            modifier = Modifier.widthIn(max = WelcomeContentMaxWidth),
        )
        Text(
            text = details,
            style = HATextStyle.Body,
            modifier = Modifier.widthIn(max = WelcomeContentMaxWidth),
        )

        content()

        Spacer(modifier = Modifier.weight(1f - positionPercentage))

        BottomButtons(
            primaryButtonText = primaryButtonText,
            onPrimaryClick = onPrimaryClick,
            onLearnMoreClick = onLearnMoreClick,
        )
    }
}

@Composable
private fun BottomButtons(
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    onLearnMoreClick: suspend () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        HAAccentButton(
            text = primaryButtonText,
            onClick = onPrimaryClick,
            modifier = Modifier.fillMaxWidth(),
        )

        HAPlainButton(
            text = stringResource(commonR.string.welcome_learn_more),
            onClick = {
                coroutineScope.launch {
                    onLearnMoreClick()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HADimens.SPACE6),
        )
    }
}
