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
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HAColorScheme
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.compose.theme.defaultRippleAlpha

private val buttonShape = RoundedCornerShape(size = HARadius.Pill)

@Immutable
private class HAButtonColors(val buttonColors: ButtonColors, val rippleColor: Color)

@Immutable
private class HAIconButtonColors(val buttonColors: IconButtonColors, val rippleColor: Color)

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

enum class ButtonSize(internal val value: Dp) {
    SMALL(32.dp),
    MEDIUM(40.dp),
    LARGE(56.dp),
}

/**
 * Displays an accent button, typically used for the most prominent call to action on a screen.
 * The button's appearance is determined by the [variant] and the current theme.
 *
 * [Design Website](https://design.home-assistant.io/#components/ha-button)
 *
 * @param text The text label displayed on the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 * @param enabled Controls the enabled state of the button. When `false`, the button will not be clickable.
 * @param size The size of the button. Defaults to [ButtonSize.LARGE].
 * @param textOverflow How visual overflow should be handled. Defaults to [TextOverflow.Clip].
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *  If the text exceeds the given number of lines, it will be truncated according to [textOverflow].
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
    size: ButtonSize = ButtonSize.MEDIUM,
    textOverflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
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
        textOverflow = textOverflow,
        maxLines = maxLines,
        size = size,
    )
}

/**
 * Displays a filled button, which is a standard button.
 * The button's appearance is determined by the [variant] and the current theme.
 *
 * [Design Website](https://design.home-assistant.io/#components/ha-button)
 *
 * @param text The text label displayed on the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 * @param enabled Controls the enabled state of the button. When `false`, the button will not be clickable.
 * @param size The size of the button. Defaults to [ButtonSize.LARGE].
 * @param textOverflow How visual overflow should be handled. Defaults to [TextOverflow.Clip].
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *  If the text exceeds the given number of lines, it will be truncated according to [textOverflow].
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
    size: ButtonSize = ButtonSize.MEDIUM,
    textOverflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
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
        textOverflow = textOverflow,
        maxLines = maxLines,
        size = size,
    )
}

/**
 * Displays a plain button, which is typically a text-only button with no background fill.
 * The button's appearance is determined by the [variant] and the current theme.
 *
 * [Design Website](https://design.home-assistant.io/#components/ha-button)
 *
 * @param text The text label displayed on the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 * @param enabled Controls the enabled state of the button. When `false`, the button will not be clickable.
 * @param size The size of the button. Defaults to [ButtonSize.LARGE].
 * @param textOverflow How visual overflow should be handled. Defaults to [TextOverflow.Clip].
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *  If the text exceeds the given number of lines, it will be truncated according to [textOverflow].
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
    size: ButtonSize = ButtonSize.MEDIUM,
    textOverflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    prefix: @Composable (BoxScope.() -> Unit)? = null,
    suffix: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = LocalHAColorScheme.current.plainButtonColorsFromVariant(variant)

    RippleConfigurationLocalProvider(colors.rippleColor) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors.buttonColors,
            modifier = Modifier
                .widthIn(max = MaxButtonWidth)
                .then(modifier)
                .heightIn(min = size.value),
            contentPadding = PaddingValues.Zero,
            shape = buttonShape,
        ) {
            ButtonContent(
                text = text,
                prefix = prefix,
                suffix = suffix,
                textOverflow = textOverflow,
                maxLines = maxLines,
            )
        }
    }
}

/**
 * Displays an icon button, which is a button containing only an icon with no text label.
 * The button's appearance is determined by the [variant] and the current theme.
 *
 * @param icon The [ImageVector] icon to be displayed in the button.
 * @param onClick The lambda function to be executed when the button is clicked.
 * @param contentDescription The content description for accessibility purposes.
 * @param modifier Optional [androidx.compose.ui.Modifier] to be applied to the button.
 * @param variant The [ButtonVariant] that determines the button's color scheme. Defaults to [ButtonVariant.PRIMARY].
 * @param enabled Controls the enabled state of the button. When `false`, the button will not be clickable.
 */
@Composable
fun HAIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = LocalHAColorScheme.current.iconButtonColorsFromVariant(variant)

    RippleConfigurationLocalProvider(colors.rippleColor) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            // We only support one size for now
            modifier = modifier.size(48.dp),
            colors = colors.buttonColors,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                // We only support one size for now
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun HABaseButton(
    text: String,
    onClick: () -> Unit,
    colors: HAButtonColors,
    size: ButtonSize,
    enabled: Boolean,
    textOverflow: TextOverflow,
    maxLines: Int,
    prefix: @Composable (BoxScope.() -> Unit)?,
    suffix: @Composable (BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    RippleConfigurationLocalProvider(colors.rippleColor) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = colors.buttonColors,
            modifier = Modifier
                .widthIn(max = MaxButtonWidth)
                .then(modifier)
                .heightIn(min = size.value),
            contentPadding = PaddingValues.Zero,
            shape = buttonShape,
        ) {
            ButtonContent(
                text = text,
                prefix = prefix,
                suffix = suffix,
                textOverflow = textOverflow,
                maxLines = maxLines,
            )
        }
    }
}

/**
 * Provide a custom ripple configuration based on [rippleColor].
 */
@Composable
private fun RippleConfigurationLocalProvider(rippleColor: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = rippleColor,
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
                        ButtonDecoratorType.PREFIX -> Modifier.padding(start = HADimens.SPACE2)
                        ButtonDecoratorType.SUFFIX -> Modifier.padding(end = HADimens.SPACE2)
                    },
                )
                .size(HADimens.SPACE6),
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
    textOverflow: TextOverflow,
    maxLines: Int,
) {
    ButtonDecorator(ButtonDecoratorType.PREFIX, prefix)
    Text(
        text = text,
        style = HATextStyle.Button,
        overflow = textOverflow,
        maxLines = maxLines,
        modifier = Modifier
            .padding(
                // Adjust padding based on the presence of prefix/suffix
                start = if (prefix != null) HADimens.SPACE1 else HADimens.SPACE4,
                end = if (suffix != null) HADimens.SPACE1 else HADimens.SPACE4,
            )
            // Apply a small padding that is only visible when the text is going to be on multiple lines,
            // to avoid touching the border of the background.
            .padding(vertical = HADimens.SPACE1)
            .weight(1f, fill = false), // Allow text to take available space but not fill it
    )
    ButtonDecorator(ButtonDecoratorType.SUFFIX, suffix)
}



private fun HAColorScheme.accentButtonColorsFromVariant(variant: ButtonVariant): HAButtonColors {
    return when (variant) {
        ButtonVariant.PRIMARY -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillPrimaryLoudResting,
                    contentColor = colorOnPrimaryLoud,
                    disabledContainerColor = colorFillDisabledLoudResting,
                    disabledContentColor = colorOnDisabledLoud,
                ),
                colorFillPrimaryLoudHover,
            )
        }

        ButtonVariant.NEUTRAL -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillNeutralLoudResting,
                    contentColor = colorOnNeutralLoud,
                    disabledContainerColor = colorFillDisabledLoudResting,
                    disabledContentColor = colorOnDisabledLoud,
                ),
                colorFillNeutralLoudHover,
            )
        }

        ButtonVariant.DANGER -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillDangerLoudResting,
                    contentColor = colorOnDangerLoud,
                    disabledContainerColor = colorFillDisabledLoudResting,
                    disabledContentColor = colorOnDisabledLoud,
                ),
                colorFillDangerLoudHover,
            )
        }

        ButtonVariant.WARNING -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillWarningLoudResting,
                    contentColor = colorOnWarningLoud,
                    disabledContainerColor = colorFillDisabledLoudResting,
                    disabledContentColor = colorOnDisabledLoud,
                ),
                colorFillWarningLoudHover,
            )
        }

        ButtonVariant.SUCCESS -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillSuccessLoudResting,
                    contentColor = colorOnSuccessLoud,
                    disabledContainerColor = colorFillDisabledLoudResting,
                    disabledContentColor = colorOnDisabledLoud,
                ),
                colorFillSuccessLoudHover,
            )
        }
    }
}

private fun HAColorScheme.filledButtonColorsFromVariant(variant: ButtonVariant): HAButtonColors {
    return when (variant) {
        ButtonVariant.PRIMARY -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillPrimaryNormalResting,
                    contentColor = colorOnPrimaryNormal,
                    disabledContainerColor = colorFillDisabledNormalResting,
                    disabledContentColor = colorOnDisabledNormal,
                ),
                colorFillPrimaryNormalHover,
            )
        }

        ButtonVariant.NEUTRAL -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillNeutralNormalResting,
                    contentColor = colorOnNeutralNormal,
                    disabledContainerColor = colorFillDisabledNormalResting,
                    disabledContentColor = colorOnDisabledNormal,
                ),
                colorFillNeutralNormalHover,
            )
        }

        ButtonVariant.DANGER -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillDangerNormalResting,
                    contentColor = colorOnDangerNormal,
                    disabledContainerColor = colorFillDisabledNormalResting,
                    disabledContentColor = colorOnDisabledNormal,
                ),
                colorFillDangerNormalHover,
            )
        }

        ButtonVariant.WARNING -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillWarningNormalResting,
                    contentColor = colorOnWarningNormal,
                    disabledContainerColor = colorFillDisabledNormalResting,
                    disabledContentColor = colorOnDisabledNormal,
                ),
                colorFillWarningNormalHover,
            )
        }

        ButtonVariant.SUCCESS -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = colorFillSuccessNormalResting,
                    contentColor = colorOnSuccessNormal,
                    disabledContainerColor = colorFillDisabledNormalResting,
                    disabledContentColor = colorOnDisabledNormal,
                ),
                colorFillSuccessNormalHover,
            )
        }
    }
}

private fun HAColorScheme.plainButtonColorsFromVariant(variant: ButtonVariant): HAButtonColors {
    return when (variant) {
        ButtonVariant.PRIMARY -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorOnPrimaryNormal,
                    disabledContainerColor = colorFillDisabledQuietResting,
                    disabledContentColor = colorOnDisabledQuiet,
                ),
                colorFillPrimaryQuietHover,
            )
        }

        ButtonVariant.NEUTRAL -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorOnNeutralNormal,
                    disabledContainerColor = colorFillDisabledQuietResting,
                    disabledContentColor = colorOnDisabledQuiet,
                ),
                colorFillNeutralQuietHover,
            )
        }

        ButtonVariant.DANGER -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorOnDangerNormal,
                    disabledContainerColor = colorFillDisabledQuietResting,
                    disabledContentColor = colorOnDisabledQuiet,
                ),
                colorFillDangerQuietHover,
            )
        }

        ButtonVariant.WARNING -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorOnWarningNormal,
                    disabledContainerColor = colorFillDisabledQuietResting,
                    disabledContentColor = colorOnDisabledQuiet,
                ),
                colorFillWarningQuietHover,
            )
        }

        ButtonVariant.SUCCESS -> {
            HAButtonColors(
                ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorOnSuccessNormal,
                    disabledContainerColor = colorFillDisabledQuietResting,
                    disabledContentColor = colorOnDisabledQuiet,
                ),
                colorFillSuccessQuietHover,
            )
        }
    }
}

private fun HAColorScheme.iconButtonColorsFromVariant(variant: ButtonVariant): HAIconButtonColors {
    return when (variant) {
        ButtonVariant.PRIMARY -> HAIconButtonColors(
            IconButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorOnPrimaryNormal,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colorOnDisabledNormal,
            ),
            colorFillPrimaryQuietHover,
        )
        ButtonVariant.NEUTRAL -> HAIconButtonColors(
            IconButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorOnNeutralQuiet,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colorOnDisabledNormal,
            ),
            colorFillNeutralQuietHover,
        )
        ButtonVariant.DANGER -> HAIconButtonColors(
            IconButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorOnDangerQuiet,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colorOnDisabledNormal,
            ),
            colorFillDangerNormalHover,
        )
        ButtonVariant.WARNING -> HAIconButtonColors(
            IconButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorOnWarningQuiet,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colorOnDisabledNormal,
            ),
            colorFillWarningNormalHover,
        )
        ButtonVariant.SUCCESS -> HAIconButtonColors(
            IconButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorOnSuccessQuiet,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colorOnDisabledNormal,
            ),
            colorFillSuccessNormalHover,
        )
    }
}
