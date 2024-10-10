package io.homeassistant.companion.android.onboarding.welcome

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.OnboardingScreen
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@Composable
fun WelcomeView(
    onContinue: () -> Unit
) {
    OnboardingScreen(Modifier.verticalScroll(rememberScrollState())) {
        Image(
            painter = painterResource(id = R.drawable.app_icon_round),
            contentDescription = stringResource(
                id = commonR.string.app_name
            ),
            modifier = Modifier
                .size(width = 274.dp, height = 202.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            text = stringResource(commonR.string.welcome_hass)
        )
        Text(
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            text = stringResource(commonR.string.welcome_hass_desc),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
        val annotatedString = buildAnnotatedString {
            pushStringAnnotation("learn", "https://www.home-assistant.io")
            withStyle(
                style = SpanStyle(
                    color = MaterialTheme.colors.primary,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(stringResource(id = commonR.string.learn_more))
            }
            pop()
        }
        val uriHandler = LocalUriHandler.current
        ClickableText(
            text = annotatedString,
            onClick = {
                annotatedString.getStringAnnotations("learn", it, it).firstOrNull()?.let { link ->
                    uriHandler.openUri(link.item)
                }
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onContinue
        ) {
            Text(text = stringResource(id = commonR.string.continue_connect))
        }
    }
}

@Composable
@Preview(showSystemUi = true)
@Preview(showSystemUi = true, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewWelcome() {
    HomeAssistantAppTheme {
        WelcomeView(onContinue = {})
    }
}
