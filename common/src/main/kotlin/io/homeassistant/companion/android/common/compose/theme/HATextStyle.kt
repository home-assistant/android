package io.homeassistant.companion.android.common.compose.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HATextStyle.Body
import io.homeassistant.companion.android.common.compose.theme.HATextStyle.BodyMedium
import io.homeassistant.companion.android.common.compose.theme.HATextStyle.Button
import io.homeassistant.companion.android.common.compose.theme.HATextStyle.Headline
import io.homeassistant.companion.android.common.compose.theme.HATextStyle.UserInput

/**
 * Object defining the different text styles used in the Home Assistant app.
 * Each text style is a combination of font style, font size, line height, font weight, text align, and letter spacing.
 *
 * Available text styles:
 * - [Headline]: Used for main titles and headings.
 * - [Body]: Default text style for body content.
 * - [BodyMedium]: A variation of the Body style with medium font size.
 * - [UserInput]: Text style for user input fields.
 * - [Button]: Text style for button labels.
 */
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
        letterSpacing = 0.sp,
    )

    val BodyMedium = Body.copy(
        fontSize = HAFontSize.M,
        lineHeight = HAFontSize.XL,
    )

    val UserInput = Body.copy(
        textAlign = TextAlign.Start,
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
