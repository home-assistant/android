package io.homeassistant.companion.android.onboarding.welcome

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.google.accompanist.themeadapter.material.MdcTheme
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun WelcomeView(
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon_round),
            contentDescription = stringResource(
                id = commonR.string.app_name
            ),
            modifier = Modifier
                .size(width = 274.dp, height = 202.dp)
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 15.dp)
        )
        Text(
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            text = stringResource(commonR.string.welcome_hass),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        )
        Text(
            fontSize = 17.sp,
            text = stringResource(commonR.string.welcome_hass_desc),
            modifier = Modifier
                .padding(bottom = 15.dp, start = 30.dp, end = 20.dp, top = 10.dp)
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
            modifier = Modifier
                .padding(bottom = 15.dp)
                .align(Alignment.CenterHorizontally)
        )

        Button(
            onClick = onContinue,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        ) {
            Text(text = stringResource(id = commonR.string.continue_connect))
        }
    }
}

@Composable
@Preview(showSystemUi = true)
@Preview(showSystemUi = true, uiMode = UI_MODE_NIGHT_YES)
private fun PreviewWelcome() {
    MdcTheme {
        WelcomeView(onContinue = {})
    }
}
