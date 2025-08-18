@file:OptIn(ExperimentalMaterial3Api::class)

package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HAButtonColors
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.compose.theme.defaultRippleAlpha

private val buttonShape = RoundedCornerShape(size = HARadius.Pill)
private val buttonContentPadding = PaddingValues(vertical = HASpacing.M)

/**
 * Defines the visual styling variants for Home Assistant buttons.
 * These variants typically map to different color schemes or semantic meanings.
 */
enum class ButtonVariant {
    /** The default, primary action button style. */
    PRIMARY,

    /** A neutral button style, often used for secondary actions. */
    NEUTRAL,

    /** A button style indicating a potentially destructive or dangerous action. */
    DANGER,

    /** A button style used to warn the user about a potential issue. */
    WARNING,

    /** A button style indicating a successful operation or positive outcome. */
    SUCCESS,
}

/**
 * Displays an accent button, typically used for the most prominent call to action on a screen.
 * The button's appearance is determined by the [variant] and the current theme.
 *
 * @see https://design.home-assistant.io/#components/ha-button
 *
 * @param text The text label displayed on the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 * @param enabled Controls the enabled state of the button. When `false`, the button will not be clickable.
 * @param prefix Optional composable content to be displayed at the start of the button, before the text.
 * @param suffix Optional composable content to be displayed at the end of the button, after the text.
 */
@Composable
fun HAAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    prefix: @Composable (BoxScope.() -> Unit)? = null,
    suffix: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = LocalHAColorScheme.current.accentButtonColorsFromVariant(variant)

    HABaseButton(
        text = text,
        onClick = onClick,
        colors = colors,
        modifier = modifier,
        enabled = enabled,
        prefix = prefix,
        suffix = suffix,
    )
}

/**
 * Displays a filled button, which is a standard button.
 * The button's appearance is determined by the [variant] and the current theme.
 *
 * @see https://design.home-assistant.io/#components/ha-button
 *
 * @param text The text label displayed on the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 * @param enabled Controls the enabled state of the button. When `false`, the button will not be clickable.
 * @param prefix Optional composable content to be displayed at the start of the button, before the text.
 * @param suffix Optional composable content to be displayed at the end of the button, after the text.
 */
@Composable
fun HAFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    prefix: @Composable (BoxScope.() -> Unit)? = null,
    suffix: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = LocalHAColorScheme.current.filledButtonColorsFromVariant(variant)

    HABaseButton(
        text = text,
        onClick = onClick,
        colors = colors,
        modifier = modifier,
        enabled = enabled,
        prefix = prefix,
        suffix = suffix,
    )
}

/**
 * Displays a plain button, which is typically a text-only button with no background fill.
 * The button's appearance is determined by the [variant] and the current theme.
 *
 * @see https://design.home-assistant.io/#components/ha-button
 *
 * @param text The text label displayed on the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 * @param enabled Controls the enabled state of the button. When `false`, the button will not be clickable.
 * @param prefix Optional composable content to be displayed at the start of the button, before the text.
 * @param suffix Optional composable content to be displayed at the end of the button, after the text.
 */
@Composable
fun HAPlainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    prefix: @Composable (BoxScope.() -> Unit)? = null,
    suffix: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = LocalHAColorScheme.current.plainButtonColorsFromVariant(variant)

    RippleConfigurationLocalProvider(colors) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors.buttonColors,
            modifier = modifier.widthIn(max = MaxButtonWidth),
            contentPadding = buttonContentPadding,
            shape = buttonShape,
        ) {
            ButtonContent(
                text = text,
                prefix = prefix,
                suffix = suffix,
            )
        }
    }
}

@Composable
private fun HABaseButton(
    text: String,
    onClick: () -> Unit,
    colors: HAButtonColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prefix: @Composable (BoxScope.() -> Unit)? = null,
    suffix: @Composable (BoxScope.() -> Unit)? = null,
) {
    RippleConfigurationLocalProvider(colors) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = colors.buttonColors,
            modifier = modifier
                .heightIn(min = HASpacing.XL)
                .widthIn(max = MaxButtonWidth),
            contentPadding = buttonContentPadding,
            shape = buttonShape,
        ) {
            ButtonContent(
                text = text,
                prefix = prefix,
                suffix = suffix,
            )
        }
    }
}

/**
 * Provide a custom ripple configuration based on [colors].
 */
@Composable
private fun RippleConfigurationLocalProvider(colors: HAButtonColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = colors.rippleColor,
            rippleAlpha = defaultRippleAlpha,
        ),
        content = content,
    )
}

private enum class ButtonDecoratorType {
    PREFIX,
    SUFFIX,
}

/**
 * Render a prefix or suffix decorator within a button's content.
 * It aligns the decorator vertically and applies appropriate padding based on its type.
 */
@Composable
private fun RowScope.ButtonDecorator(type: ButtonDecoratorType, content: @Composable (BoxScope.() -> Unit)?) {
    content?.let {
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .then(
                    when (type) {
                        ButtonDecoratorType.PREFIX -> Modifier.padding(start = HASpacing.XS)
                        ButtonDecoratorType.SUFFIX -> Modifier.padding(end = HASpacing.XS)
                    },
                )
                .size(HASpacing.XL),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * Render the content layout of a Home Assistant button,
 * including optional prefix and suffix decorators and the main text label.
 */
@Composable
private fun RowScope.ButtonContent(
    text: String,
    prefix: @Composable (BoxScope.() -> Unit)?,
    suffix: @Composable (BoxScope.() -> Unit)?,
) {
    ButtonDecorator(ButtonDecoratorType.PREFIX, prefix)
    Text(
        text = text,
        style = HATextStyle.Button,
        modifier = Modifier
            .padding(
                // Adjust padding based on the presence of prefix/suffix
                start = if (prefix != null) HASpacing.X2S else HASpacing.M,
                end = if (suffix != null) HASpacing.X2S else HASpacing.M,
            )
            .weight(1f, fill = false), // Allow text to take available space but not fill it
    )
    ButtonDecorator(ButtonDecoratorType.SUFFIX, suffix)
}
