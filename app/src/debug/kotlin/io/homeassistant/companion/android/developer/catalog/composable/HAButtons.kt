package io.homeassistant.companion.android.developer.catalog.composable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.developer.catalog.theme.HAButtonColors
import io.homeassistant.companion.android.developer.catalog.theme.HARadius
import io.homeassistant.companion.android.developer.catalog.theme.HASpacing
import io.homeassistant.companion.android.developer.catalog.theme.HATextStyle
import io.homeassistant.companion.android.developer.catalog.theme.LocalHAColorScheme
import io.homeassistant.companion.android.developer.catalog.theme.MaxButtonWidth
import io.homeassistant.companion.android.developer.catalog.theme.defaultRippleAlpha

enum class ButtonVariant {
    PRIMARY,
    NEUTRAL,
    DANGER,
    WARNING,
    SUCCESS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HAAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = LocalHAColorScheme.current.accentButtonColorsFromVariant(variant)

    HABaseButton(
        text = text,
        onClick = onClick,
        colors = colors,
        modifier = modifier,
        enabled = enabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HAFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = LocalHAColorScheme.current.filledButtonColorsFromVariant(variant)

    HABaseButton(
        text = text,
        onClick = onClick,
        colors = colors,
        modifier = modifier,
        enabled = enabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HAPlainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    val colors = LocalHAColorScheme.current.plainButtonColorsFromVariant(variant)

    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = colors.rippleColor,
            rippleAlpha = defaultRippleAlpha(),
        ),
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors.buttonColors,
            modifier = modifier.widthIn(max = MaxButtonWidth),
            contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
            shape = RoundedCornerShape(size = HARadius.XL),
        ) {
            Text(
                text = text,
                style = HATextStyle.Button,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HABaseButton(
    text: String,
    onClick: () -> Unit,
    colors: HAButtonColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(
            color = colors.rippleColor,
            rippleAlpha = defaultRippleAlpha(),
        ),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = colors.buttonColors,
            modifier = modifier
                .widthIn(max = MaxButtonWidth)
                .heightIn(min = HASpacing.XL),
            contentPadding = PaddingValues(horizontal = HASpacing.M, vertical = HASpacing.M),
            shape = RoundedCornerShape(size = HARadius.Pill),
        ) {
            Text(
                text = text,
                style = HATextStyle.Button,
            )
        }
    }
}
