package io.homeassistant.companion.android.developer.catalog.composable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.developer.catalog.theme.HARadius
import io.homeassistant.companion.android.developer.catalog.theme.HASpacing
import io.homeassistant.companion.android.developer.catalog.theme.HATextStyle
import io.homeassistant.companion.android.developer.catalog.theme.LocalHAColorScheme
import io.homeassistant.companion.android.developer.catalog.theme.MaxButtonWidth

enum class ButtonVariant {
    PRIMARY,
    NEUTRAL,
    DANGER,
    WARNING,
    SUCCESS,
}

@Composable
fun HAAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = LocalHAColorScheme.current.buttonColorsFromVariant(variant),
        modifier = modifier
            .widthIn(max = MaxButtonWidth),
        contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
        shape = RoundedCornerShape(size = HARadius.XL),
    ) {
        Text(
            text = text,
            style = HATextStyle.Button,
        )
    }
}

@Composable
fun HAFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = LocalHAColorScheme.current.buttonColorsFromVariant(variant),
        modifier = modifier
            .widthIn(max = MaxButtonWidth),
        contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
        shape = RoundedCornerShape(size = HARadius.XL),
    ) {
        Text(
            text = text,
            style = HATextStyle.Button,
        )
    }
}

@Composable
fun HAPlainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = LocalHAColorScheme.current.buttonColorsFromVariant(variant),
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
