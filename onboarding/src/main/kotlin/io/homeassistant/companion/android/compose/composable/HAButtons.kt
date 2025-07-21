package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.onboarding.theme.HARadius
import io.homeassistant.companion.android.onboarding.theme.HASpacing
import io.homeassistant.companion.android.onboarding.theme.HATextStyle
import io.homeassistant.companion.android.onboarding.theme.MaxButtonWidth

@Composable
fun HAButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .widthIn(max = MaxButtonWidth)
            .fillMaxWidth(),
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
fun HATextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.widthIn(max = MaxButtonWidth)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = HASpacing.XL, vertical = HASpacing.M),
        shape = RoundedCornerShape(size = HARadius.XL),
    ) {
        Text(
            text = text,
            style = HATextStyle.Button,
        )
    }
}
