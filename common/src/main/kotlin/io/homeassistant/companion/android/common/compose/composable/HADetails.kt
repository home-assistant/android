package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Displays an expandable details section with an animated arrow indicator.
 * This composable provides a collapsible container for content, with a title bar that users can click to expand or collapse.
 * The arrow icon rotates smoothly when transitioning between states.
 *
 * @param title The text label displayed in the title bar.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the details container.
 * @param defaultExpanded Controls the initial expanded state. When `true`, the content will be visible on first render. Defaults to `false`.
 * @param content The composable content to be displayed inside the expandable section when expanded.
 */
@Composable
fun HADetails(
    title: String,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    val shape = RoundedCornerShape(HARadius.L)

    Column(
        modifier = modifier
            .border(HABorderWidth.S, color = LocalHAColorScheme.current.colorBorderNeutralQuiet, shape = shape)
            .background(color = LocalHAColorScheme.current.colorSurfaceDefault, shape = shape)
            .clip(shape)
            .clickable { isExpanded = !isExpanded }
            .fillMaxWidth()
            .padding(horizontal = HADimens.SPACE3, vertical = HADimens.SPACE4),
    ) {
        ExpandableSectionTitle(isExpanded = isExpanded, title = title)

        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = HADimens.SPACE2),
            visible = isExpanded,
        ) {
            content()
        }
    }
}

@Composable
private fun ExpandableSectionTitle(isExpanded: Boolean, title: String, modifier: Modifier = Modifier) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "Icon rotation",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            tint = LocalHAColorScheme.current.colorOnNeutralQuiet,
            contentDescription = null, // The whole section is already clickable
            modifier = Modifier.graphicsLayer { rotationZ = rotationAngle },
        )
        Text(
            text = title,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            modifier = Modifier.weight(1f),
        )
    }
}

@PreviewLightDark
@Composable
private fun HADetailsPreview_close() {
    HAThemeForPreview {
        HADetails("hello") {
        }
    }
}

@PreviewLightDark
@Composable
private fun HADetailsPreview_open() {
    HAThemeForPreview {
        HADetails("hello", defaultExpanded = true) {
            Text("With some content")
        }
    }
}
