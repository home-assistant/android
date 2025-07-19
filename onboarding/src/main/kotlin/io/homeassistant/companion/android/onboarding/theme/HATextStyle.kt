package io.homeassistant.companion.android.onboarding.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

object HATextStyle {

    val Headline = TextStyle(
        fontStyle = FontStyle.Normal,
        fontSize = HAFontSize.X4L,
        lineHeight = HAFontSize.X5L,
        fontWeight = FontWeight.W500,
        textAlign = TextAlign.Center,
    )

    val Body = TextStyle(
        fontStyle = FontStyle.Normal,
        fontSize = HAFontSize.L,
        lineHeight = HAFontSize.X2L,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        letterSpacing = 0.5.sp,
    )

    val Button = TextStyle(
        fontStyle = FontStyle.Normal,
        fontSize = HAFontSize.L,
        lineHeight = HAFontSize.X2L,
        fontWeight = FontWeight.W500,
        textAlign = TextAlign.Center,
        letterSpacing = 0.15.sp,
    )
}
