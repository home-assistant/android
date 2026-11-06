package io.homeassistant.companion.android.common.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HATextStyleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Given text styles when theme changes then colours and inherited properties follow the theme`() {
        var darkTheme by mutableStateOf(false)
        lateinit var styles: TextStyles
        composeRule.setContent {
            HATheme(darkTheme = darkTheme) {
                CaptureTextStyles { styles = it }
            }
        }
        composeRule.waitForIdle()
        val initialStyles = styles
        assertStyles(styles, LightHAColorScheme)

        composeRule.runOnIdle { darkTheme = true }
        composeRule.waitForIdle()
        assertStyles(styles, DarkHAColorScheme)

        composeRule.runOnIdle { darkTheme = false }
        composeRule.waitForIdle()
        assertStyles(styles, LightHAColorScheme)
        assertEquals(initialStyles, styles)
    }

    @Test
    fun `Given nested themes when reading text styles then the nearest provider is used`() {
        lateinit var outerStyles: TextStyles
        lateinit var innerStyles: TextStyles
        lateinit var stylesAfterInnerTheme: TextStyles
        composeRule.setContent {
            HATheme(darkTheme = false) {
                CaptureTextStyles { outerStyles = it }
                HATheme(darkTheme = true) {
                    CaptureTextStyles { innerStyles = it }
                }
                CaptureTextStyles { stylesAfterInnerTheme = it }
            }
        }
        composeRule.waitForIdle()

        assertStyles(outerStyles, LightHAColorScheme)
        assertStyles(innerStyles, DarkHAColorScheme)
        assertEquals(outerStyles, stylesAfterInnerTheme)
    }

    private fun assertStyles(styles: TextStyles, scheme: HAColorScheme) {
        assertEquals(scheme.colorTextPrimary, styles.headline.color)
        assertEquals(scheme.colorTextPrimary, styles.headlineMedium.color)
        assertEquals(scheme.colorTextSecondary, styles.body.color)
        assertEquals(scheme.colorTextLink, styles.link.style?.color)
        assertEquals(
            styles.body.copy(fontSize = HAFontSize.M, lineHeight = HAFontSize.XL),
            styles.bodyMedium,
        )
        assertEquals(styles.body.copy(textAlign = TextAlign.Start), styles.userInput)
    }

    @Composable
    private fun CaptureTextStyles(onStyles: (TextStyles) -> Unit) {
        val styles = TextStyles(
            headline = HATextStyle.Headline,
            headlineMedium = HATextStyle.HeadlineMedium,
            body = HATextStyle.Body,
            bodyMedium = HATextStyle.BodyMedium,
            userInput = HATextStyle.UserInput,
            link = HATextStyle.Link,
        )
        SideEffect { onStyles(styles) }
    }

    private data class TextStyles(
        val headline: TextStyle,
        val headlineMedium: TextStyle,
        val body: TextStyle,
        val bodyMedium: TextStyle,
        val userInput: TextStyle,
        val link: TextLinkStyles,
    )
}
