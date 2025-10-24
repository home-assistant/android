package io.homeassistant.companion.android.common.compose.theme

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.theme.HAColors.Transparent

/**
 * Defines the core color palette for the Home Assistant application.
 *
 * ## Core Color Tokens
 *
 * Core color tokens are the foundational color values used throughout the design system.
 * These tokens represent raw, brand-independent colors such as grayscale shades, base hues, and accent tones.
 * Core tokens shouldn't be tied to any specific UI purpose or role.
 * Instead, they serve as building blocks from which semantic tokens are derived.
 *
 * ### Usage
 * These colors should not be used directly in UI components. Instead, they serve as the foundation
 * for semantic color tokens defined in [HAColorScheme]. This approach ensures consistency and
 * simplifies color selection by providing meaningful names for UI elements.
 *
 * Changes to core tokens will cascade into semantic tokens that reference them, enabling flexible theming and
 * consistent design language. Please note that these core tokens are not intended to be used directly in components
 * or styles (with some exception like [Transparent]).
 *
 * When adding new color tokens, it's crucial to synchronize with the design team, as well as frontend and
 * iOS development teams, to maintain a unified visual language across all platforms.
 */
private object HAColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Transparent = Color(0x00FFFFFF)

    // Primary
    val Primary05 = Color(0xFF001721)
    val Primary10 = Color(0xFF002E3E)
    val Primary20 = Color(0xFF004156)
    val Primary30 = Color(0xFF006787)
    val Primary40 = Color(0xFF009AC7)
    val Primary50 = Color(0xFF18BCF2)
    val Primary60 = Color(0xFF37C8FD)
    val Primary70 = Color(0xFF7BD4FB)
    val Primary80 = Color(0xFFB9E6FC)
    val Primary90 = Color(0xFFDFF3FC)
    val Primary95 = Color(0xFFEFF9FE)

    // Blue
    val Blue05 = Color(0xFF000F35)
    val Blue10 = Color(0xFF001A4E)
    val Blue20 = Color(0xFF002D77)
    val Blue30 = Color(0xFF003F9C)
    val Blue40 = Color(0xFF0053C0)
    val Blue50 = Color(0xFF0071EC)
    val Blue60 = Color(0xFF3E96FF)
    val Blue70 = Color(0xFF6EB3FF)
    val Blue80 = Color(0xFF9FCEFF)
    val Blue90 = Color(0xFFD1E8FF)
    val Blue95 = Color(0xFFE8F3FF)

    // Brand
    val Brand05 = Color(0xFF00222F)
    val Brand10 = Color(0xFF003D51)
    val Brand20 = Color(0xFF004E67)
    val Brand30 = Color(0xFF007093)
    val Brand40 = Color(0xFF00A4D4)
    val Brand50 = Color(0xFF1FBCF1)
    val Brand60 = Color(0xFF37C8FD)
    val Brand70 = Color(0xFF7BD4FB)
    val Brand80 = Color(0xFFB9E6FC)
    val Brand90 = Color(0xFFDFF3FC)
    val Brand95 = Color(0xFFEFF9FE)

    // Cyan
    val Cyan05 = Color(0xFF00151B)
    val Cyan10 = Color(0xFF002129)
    val Cyan20 = Color(0xFF003844)
    val Cyan30 = Color(0xFF014C5B)
    val Cyan40 = Color(0xFF026274)
    val Cyan50 = Color(0xFF078098)
    val Cyan60 = Color(0xFF00A3C0)
    val Cyan70 = Color(0xFF2FBEDC)
    val Cyan80 = Color(0xFF7FD6EC)
    val Cyan90 = Color(0xFFC5ECF7)
    val Cyan95 = Color(0xFFE3F6FB)

    // Green
    val Green05 = Color(0xFF031608)
    val Green10 = Color(0xFF052310)
    val Green20 = Color(0xFF0A3A1D)
    val Green30 = Color(0xFF0A5027)
    val Green40 = Color(0xFF036730)
    val Green50 = Color(0xFF00883C)
    val Green60 = Color(0xFF00AC49)
    val Green70 = Color(0xFF5DC36F)
    val Green80 = Color(0xFF93DA98)
    val Green90 = Color(0xFFC2F2C1)
    val Green95 = Color(0xFFE3F9E3)

    // Indigo
    val Indigo05 = Color(0xFF0D0A3A)
    val Indigo10 = Color(0xFF181255)
    val Indigo20 = Color(0xFF292381)
    val Indigo30 = Color(0xFF3933A7)
    val Indigo40 = Color(0xFF4945CB)
    val Indigo50 = Color(0xFF6163F2)
    val Indigo60 = Color(0xFF808AFF)
    val Indigo70 = Color(0xFF9DA9FF)
    val Indigo80 = Color(0xFFBCC7FF)
    val Indigo90 = Color(0xFFDFe5FF)
    val Indigo95 = Color(0xFFF0F2FF)

    // Neutral
    val Neutral05 = Color(0xFF141414)
    val Neutral10 = Color(0xFF202020)
    val Neutral20 = Color(0xFF363636)
    val Neutral30 = Color(0xFF4A4A4A)
    val Neutral40 = Color(0xFF5E5E5E)
    val Neutral50 = Color(0xFF7A7A7A)
    val Neutral60 = Color(0xFF989898)
    val Neutral70 = Color(0xFFB1B1B1)
    val Neutral80 = Color(0xFFCCCCCC)
    val Neutral90 = Color(0xFFE6E6E6)
    val Neutral95 = Color(0xFFF3F3F3)

    // Orange
    val Orange05 = Color(0xFF280700)
    val Orange10 = Color(0xFF3B0F00)
    val Orange20 = Color(0xFF5E1C00)
    val Orange30 = Color(0xFF7E2900)
    val Orange40 = Color(0xFF9D3800)
    val Orange50 = Color(0xFFC94E00)
    val Orange60 = Color(0xFFF36D00)
    val Orange70 = Color(0xFFFF9342)
    val Orange80 = Color(0xFFFFBB89)
    val Orange90 = Color(0xFFFFE0C8)
    val Orange95 = Color(0xFFFFF0E4)

    // Pink
    val Pink05 = Color(0xFF28041A)
    val Pink10 = Color(0xFF3C0828)
    val Pink20 = Color(0xFF5E1342)
    val Pink30 = Color(0xFF7D1E58)
    val Pink40 = Color(0xFF9E2A6C)
    val Pink50 = Color(0xFFC84382)
    val Pink60 = Color(0xFFE66BA3)
    val Pink70 = Color(0xFFF78DBF)
    val Pink80 = Color(0xFFFCB5D8)
    val Pink90 = Color(0xFFFEDDF0)
    val Pink95 = Color(0xFFFEFFF9)

    // Purple
    val Purple05 = Color(0xFF1E0532)
    val Purple10 = Color(0xFF2D0B48)
    val Purple20 = Color(0xFF491870)
    val Purple30 = Color(0xFF612692)
    val Purple40 = Color(0xFF7936B3)
    val Purple50 = Color(0xFF9951DB)
    val Purple60 = Color(0xFFB678F5)
    val Purple70 = Color(0xFFCA99FF)
    val Purple80 = Color(0xFFDDBDFF)
    val Purple90 = Color(0xFFEEDFFF)
    val Purple95 = Color(0xFFF7F0FF)

    // Red
    val Red05 = Color(0xFF2A040B)
    val Red10 = Color(0xFF3E0913)
    val Red20 = Color(0xFF631323)
    val Red30 = Color(0xFF8A132C)
    val Red40 = Color(0xFFB30532)
    val Red50 = Color(0xFFDC3146)
    val Red60 = Color(0xFFF3676C)
    val Red70 = Color(0xFFFD8F90)
    val Red80 = Color(0xFFFFB8B6)
    val Red90 = Color(0xFFFFDEDC)
    val Red95 = Color(0xFFFFF0EF)

    // Yellow
    val Yellow05 = Color(0xFF220C00)
    val Yellow10 = Color(0xFF331600)
    val Yellow20 = Color(0xFF532600)
    val Yellow30 = Color(0xFF6F3601)
    val Yellow40 = Color(0xFF8C4602)
    val Yellow50 = Color(0xFFB45F04)
    val Yellow60 = Color(0xFFDA7E00)
    val Yellow70 = Color(0xFFEF9D00)
    val Yellow80 = Color(0xFFFAC22B)
    val Yellow90 = Color(0xFFFFE495)
    val Yellow95 = Color(0xFFFEF3CD)
}

// TODO validate with design team if this color are immutable or not (not changeable by the users and also stay the same
//  in dark mode)
object HABrandColors {
    val Blue = Color(0xFF18BCF2)
    val Background = Color(0xFFF2F4F9)
}

@Immutable
class HAButtonColors(val buttonColors: ButtonColors, val rippleColor: Color)

@Immutable
class HAIconButtonColors(val buttonColors: IconButtonColors, val rippleColor: Color)

/**
 * Home Assistant Color Scheme. HA* composables use the values from this class to theme the UI.
 *
 * This class is inspired by [androidx.compose.material3.ColorScheme] and provides custom color tokens
 * aligned with the Home Assistant design system. It defines semantic color tokens, which are
 * abstractions built on top of the core color tokens found in [HAColors].
 *
 * ## Semantic color tokens
 *
 * Semantic color tokens represent colors based on their usage or purpose within the UI. They provide
 * a layer of abstraction over the core color palette, making it easier to maintain consistency
 * and adapt to different themes.
 *
 * ## Naming convention
 * Tokens are named according to their semantic role (e.g., `primary`, `success`, `warning`).
 * This naming convention enhances clarity and helps developers choose the appropriate color for
 * various UI elements.
 *
 * Semantic tokens use core tokens to reference the actual color values. This separation allows for adjustments
 * in color schemes without affecting the semantic meaning or intent.
 */
@Immutable
class HAColorScheme(
    val colorFillPrimaryLoudResting: Color,
    val colorFillPrimaryNormalResting: Color,
    val colorFillPrimaryQuietResting: Color,
    val colorFillPrimaryLoudHover: Color,
    val colorFillPrimaryNormalHover: Color,
    val colorFillPrimaryQuietHover: Color,
    val colorFillPrimaryNormalActive: Color,
    val colorOnPrimaryLoud: Color,
    val colorOnPrimaryNormal: Color,
    val colorOnPrimaryQuiet: Color,

    val colorFillNeutralLoudResting: Color,
    val colorFillNeutralNormalResting: Color,
    val colorFillNeutralQuietResting: Color,
    val colorFillNeutralLoudHover: Color,
    val colorFillNeutralNormalHover: Color,
    val colorFillNeutralQuietHover: Color,
    val colorOnNeutralLoud: Color,
    val colorOnNeutralNormal: Color,
    val colorOnNeutralQuiet: Color,

    val colorFillDangerLoudResting: Color,
    val colorFillDangerNormalResting: Color,
    val colorFillDangerQuietResting: Color,
    val colorFillDangerLoudHover: Color,
    val colorFillDangerNormalHover: Color,
    val colorFillDangerQuietHover: Color,
    val colorOnDangerLoud: Color,
    val colorOnDangerNormal: Color,
    val colorOnDangerQuiet: Color,

    val colorFillWarningLoudResting: Color,
    val colorFillWarningNormalResting: Color,
    val colorFillWarningQuietResting: Color,
    val colorFillWarningLoudHover: Color,
    val colorFillWarningNormalHover: Color,
    val colorFillWarningQuietHover: Color,
    val colorOnWarningLoud: Color,
    val colorOnWarningNormal: Color,
    val colorOnWarningQuiet: Color,

    val colorFillSuccessLoudResting: Color,
    val colorFillSuccessNormalResting: Color,
    val colorFillSuccessQuietResting: Color,
    val colorFillSuccessLoudHover: Color,
    val colorFillSuccessNormalHover: Color,
    val colorFillSuccessQuietHover: Color,
    val colorOnSuccessLoud: Color,
    val colorOnSuccessNormal: Color,
    val colorOnSuccessQuiet: Color,

    val colorFillDisabledLoudResting: Color,
    val colorFillDisabledNormalResting: Color,
    val colorFillDisabledQuietResting: Color,

    val colorOnDisabledLoud: Color,
    val colorOnDisabledNormal: Color,
    val colorOnDisabledQuiet: Color,

    val colorSurfaceDefault: Color,

    val colorTextPrimary: Color,
    val colorTextSecondary: Color,
    val colorTextDisabled: Color,

    val colorTextLink: Color,

    val colorBorderPrimaryNormal: Color,
    val colorBorderPrimaryLoud: Color,

    val colorBorderNeutralQuiet: Color,
    val colorBorderNeutralNormal: Color,

    val colorBorderDangerNormal: Color,

    val colorOverlayModal: Color,
) {

    fun textField(): TextFieldColors {
        return TextFieldColors(
            focusedTextColor = colorTextPrimary,
            unfocusedTextColor = colorTextPrimary,
            disabledTextColor = colorTextDisabled,
            errorTextColor = colorOnDangerQuiet,

            focusedContainerColor = colorSurfaceDefault,
            unfocusedContainerColor = colorSurfaceDefault,
            disabledContainerColor = colorFillDisabledNormalResting,
            errorContainerColor = colorSurfaceDefault,

            cursorColor = colorBorderPrimaryNormal,
            errorCursorColor = colorBorderDangerNormal,

            // TODO Change colors with design team (current value are picked approximatively)
            textSelectionColors = TextSelectionColors(
                handleColor = colorFillPrimaryLoudHover,
                backgroundColor = colorFillPrimaryLoudResting,
            ),

            focusedIndicatorColor = colorBorderPrimaryNormal,
            unfocusedIndicatorColor = colorBorderNeutralQuiet,
            disabledIndicatorColor = colorFillDisabledLoudResting,
            errorIndicatorColor = colorBorderDangerNormal,

            focusedLeadingIconColor = colorOnNeutralQuiet,
            unfocusedLeadingIconColor = colorOnNeutralQuiet,
            disabledLeadingIconColor = colorOnNeutralQuiet,
            errorLeadingIconColor = colorOnNeutralQuiet,

            focusedTrailingIconColor = colorOnNeutralQuiet,
            unfocusedTrailingIconColor = colorOnNeutralQuiet,
            disabledTrailingIconColor = colorOnNeutralQuiet,
            errorTrailingIconColor = colorOnNeutralQuiet,

            focusedLabelColor = colorTextSecondary,
            unfocusedLabelColor = colorTextSecondary,
            disabledLabelColor = colorTextDisabled,
            errorLabelColor = colorTextSecondary,

            // TODO Verify colors with design team
            focusedPlaceholderColor = colorTextPrimary,
            unfocusedPlaceholderColor = colorTextPrimary,
            disabledPlaceholderColor = colorTextPrimary,
            errorPlaceholderColor = colorTextPrimary,

            // TODO Verify colors with design team
            focusedSupportingTextColor = colorTextPrimary,
            unfocusedSupportingTextColor = colorTextPrimary,
            disabledSupportingTextColor = colorTextDisabled,
            errorSupportingTextColor = colorOnDangerQuiet,

            // TODO Verify colors with design team
            focusedPrefixColor = colorOnNeutralQuiet,
            unfocusedPrefixColor = colorOnNeutralQuiet,
            disabledPrefixColor = colorOnNeutralQuiet,
            errorPrefixColor = colorOnNeutralQuiet,

            // TODO Verify colors with design team
            focusedSuffixColor = colorOnNeutralQuiet,
            unfocusedSuffixColor = colorOnNeutralQuiet,
            disabledSuffixColor = colorOnNeutralQuiet,
            errorSuffixColor = colorOnNeutralQuiet,
        )
    }

    fun accentButtonColorsFromVariant(variant: ButtonVariant): HAButtonColors {
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

    fun filledButtonColorsFromVariant(variant: ButtonVariant): HAButtonColors {
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

    fun plainButtonColorsFromVariant(variant: ButtonVariant): HAButtonColors {
        return when (variant) {
            ButtonVariant.PRIMARY -> {
                HAButtonColors(
                    ButtonColors(
                        containerColor = Transparent,
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
                        containerColor = Transparent,
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
                        containerColor = Transparent,
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
                        containerColor = Transparent,
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
                        containerColor = Transparent,
                        contentColor = colorOnSuccessNormal,
                        disabledContainerColor = colorFillDisabledQuietResting,
                        disabledContentColor = colorOnDisabledQuiet,
                    ),
                    colorFillSuccessQuietHover,
                )
            }
        }
    }

    fun iconButtonColorsFromVariant(variant: ButtonVariant): HAIconButtonColors {
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
            // TODO validate when design is ready (current value are based on other buttons)
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
}

val DarkHAColorScheme = HAColorScheme(
    colorFillPrimaryLoudResting = HAColors.Primary40,
    colorFillPrimaryNormalResting = HAColors.Primary10,
    colorFillPrimaryQuietResting = HAColors.Primary05,
    colorFillPrimaryLoudHover = HAColors.Primary30,
    colorFillPrimaryNormalHover = HAColors.Primary20,
    colorFillPrimaryQuietHover = HAColors.Primary10,
    colorFillPrimaryNormalActive = HAColors.Primary10,
    colorOnPrimaryLoud = HAColors.White,
    colorOnPrimaryNormal = HAColors.Primary60,
    colorOnPrimaryQuiet = HAColors.Primary70,

    colorFillNeutralLoudResting = HAColors.Neutral40,
    colorFillNeutralNormalResting = HAColors.Neutral10,
    colorFillNeutralQuietResting = HAColors.Neutral05,
    colorFillNeutralLoudHover = HAColors.Neutral30,
    colorFillNeutralNormalHover = HAColors.Neutral20,
    colorFillNeutralQuietHover = HAColors.Neutral10,
    colorOnNeutralLoud = HAColors.White,
    colorOnNeutralNormal = HAColors.Neutral60,
    colorOnNeutralQuiet = HAColors.Neutral70,

    colorFillDangerLoudResting = HAColors.Red40,
    colorFillDangerNormalResting = HAColors.Red10,
    colorFillDangerQuietResting = HAColors.Red05,
    colorFillDangerLoudHover = HAColors.Red30,
    colorFillDangerNormalHover = HAColors.Red20,
    colorFillDangerQuietHover = HAColors.Red10,
    colorOnDangerLoud = HAColors.White,
    colorOnDangerNormal = HAColors.Red60,
    colorOnDangerQuiet = HAColors.Red70,

    colorFillWarningLoudResting = HAColors.Orange50,
    colorFillWarningNormalResting = HAColors.Orange10,
    colorFillWarningQuietResting = HAColors.Orange05,
    colorFillWarningLoudHover = HAColors.Orange40,
    colorFillWarningNormalHover = HAColors.Orange20,
    colorFillWarningQuietHover = HAColors.Orange10,
    colorOnWarningLoud = HAColors.White,
    colorOnWarningNormal = HAColors.Orange60,
    colorOnWarningQuiet = HAColors.Orange70,

    colorFillSuccessLoudResting = HAColors.Green40,
    colorFillSuccessNormalResting = HAColors.Green10,
    colorFillSuccessQuietResting = HAColors.Green05,
    colorFillSuccessLoudHover = HAColors.Green30,
    colorFillSuccessNormalHover = HAColors.Green20,
    colorFillSuccessQuietHover = HAColors.Green10,
    colorOnSuccessLoud = HAColors.White,
    colorOnSuccessNormal = HAColors.Green60,
    colorOnSuccessQuiet = HAColors.Green70,

    colorFillDisabledLoudResting = HAColors.Neutral30,
    colorFillDisabledNormalResting = HAColors.Neutral20,
    colorFillDisabledQuietResting = HAColors.Neutral10,
    colorOnDisabledLoud = HAColors.Neutral60,
    colorOnDisabledNormal = HAColors.Neutral60,
    colorOnDisabledQuiet = HAColors.Neutral40,

    colorSurfaceDefault = HAColors.Neutral10,

    colorTextPrimary = HAColors.White,
    colorTextSecondary = HAColors.Neutral70,
    colorTextDisabled = HAColors.Neutral60,

    colorTextLink = HAColors.Primary60,

    colorBorderPrimaryNormal = HAColors.Primary50,
    colorBorderPrimaryLoud = HAColors.Primary70,

    colorBorderNeutralQuiet = HAColors.Neutral40,
    colorBorderNeutralNormal = HAColors.Neutral50,
    colorBorderDangerNormal = HAColors.Red50,

    colorOverlayModal = HAColors.Black.copy(alpha = 0.25f),
)

val LightHAColorScheme = HAColorScheme(
    colorFillPrimaryLoudResting = HAColors.Primary40,
    colorFillPrimaryNormalResting = HAColors.Primary90,
    colorFillPrimaryQuietResting = HAColors.Primary95,
    colorFillPrimaryLoudHover = HAColors.Primary30,
    colorFillPrimaryNormalHover = HAColors.Primary20,
    colorFillPrimaryQuietHover = HAColors.Primary90,
    colorFillPrimaryNormalActive = HAColors.Primary90,
    colorOnPrimaryLoud = HAColors.White,
    colorOnPrimaryNormal = HAColors.Primary40,
    colorOnPrimaryQuiet = HAColors.Primary50,

    colorFillNeutralLoudResting = HAColors.Neutral40,
    colorFillNeutralNormalResting = HAColors.Neutral90,
    colorFillNeutralQuietResting = HAColors.Neutral95,
    colorFillNeutralLoudHover = HAColors.Neutral30,
    colorFillNeutralNormalHover = HAColors.Neutral80,
    colorFillNeutralQuietHover = HAColors.Neutral90,
    colorOnNeutralLoud = HAColors.White,
    colorOnNeutralNormal = HAColors.Neutral40,
    colorOnNeutralQuiet = HAColors.Neutral50,

    colorFillDangerLoudResting = HAColors.Red40,
    colorFillDangerNormalResting = HAColors.Red90,
    colorFillDangerQuietResting = HAColors.Red95,
    colorFillDangerLoudHover = HAColors.Red30,
    colorFillDangerNormalHover = HAColors.Red80,
    colorFillDangerQuietHover = HAColors.Red90,
    colorOnDangerLoud = HAColors.White,
    colorOnDangerNormal = HAColors.Red40,
    colorOnDangerQuiet = HAColors.Red50,

    colorFillWarningLoudResting = HAColors.Orange50,
    colorFillWarningNormalResting = HAColors.Orange90,
    colorFillWarningQuietResting = HAColors.Orange95,
    colorFillWarningLoudHover = HAColors.Orange40,
    colorFillWarningNormalHover = HAColors.Orange80,
    colorFillWarningQuietHover = HAColors.Orange90,
    colorOnWarningLoud = HAColors.White,
    colorOnWarningNormal = HAColors.Orange40,
    colorOnWarningQuiet = HAColors.Orange50,

    colorFillSuccessLoudResting = HAColors.Green40,
    colorFillSuccessNormalResting = HAColors.Green90,
    colorFillSuccessQuietResting = HAColors.Green95,
    colorFillSuccessLoudHover = HAColors.Green30,
    colorFillSuccessNormalHover = HAColors.Green80,
    colorFillSuccessQuietHover = HAColors.Green90,
    colorOnSuccessLoud = HAColors.White,
    colorOnSuccessNormal = HAColors.Green40,
    colorOnSuccessQuiet = HAColors.Green50,

    colorFillDisabledLoudResting = HAColors.Neutral80,
    colorFillDisabledNormalResting = HAColors.Neutral90,
    colorFillDisabledQuietResting = HAColors.Neutral95,
    colorOnDisabledLoud = HAColors.Neutral95,
    colorOnDisabledNormal = HAColors.Neutral70,
    colorOnDisabledQuiet = HAColors.Neutral80,

    colorSurfaceDefault = HAColors.White,

    colorTextPrimary = HAColors.Neutral05,
    colorTextSecondary = HAColors.Neutral40,
    colorTextDisabled = HAColors.Neutral60,

    colorTextLink = HAColors.Primary40,

    colorBorderPrimaryNormal = HAColors.Primary70,
    colorBorderPrimaryLoud = HAColors.Primary40,

    colorBorderNeutralQuiet = HAColors.Neutral80,
    colorBorderNeutralNormal = HAColors.Neutral70,
    colorBorderDangerNormal = HAColors.Red70,

    colorOverlayModal = HAColors.Black.copy(alpha = 0.25f),
)

val LocalHAColorScheme = staticCompositionLocalOf { LightHAColorScheme }
