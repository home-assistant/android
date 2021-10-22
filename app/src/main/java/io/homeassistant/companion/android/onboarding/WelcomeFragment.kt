package io.homeassistant.companion.android.onboarding

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import androidx.fragment.app.Fragment
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment

class WelcomeFragment : Fragment() {

    companion object {
        fun newInstance(): WelcomeFragment {
            return WelcomeFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    WelcomeSection()
                }
            }
        }
    }

    @Composable
    private fun WelcomeSection() {
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = stringResource(
                    id = R.string.app_name
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
                text = stringResource(R.string.welcome_hass),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                fontSize = 17.sp,
                text = stringResource(R.string.welcome_hass_desc),
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
                    append(stringResource(id = R.string.learn_more))
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
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val discoveryFragment = DiscoveryFragment.newInstance()
                        discoveryFragment.retainInstance = true
                        parentFragmentManager
                            .beginTransaction()
                            .replace(R.id.content, discoveryFragment)
                            .addToBackStack("Welcome")
                            .commit()
                    } else {
                        val manualFragment = ManualSetupFragment.newInstance()
                        manualFragment.retainInstance = true
                        parentFragmentManager
                            .beginTransaction()
                            .replace(R.id.content, manualFragment)
                            .addToBackStack("Welcome")
                            .commit()
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(id = R.string.continue_connect))
            }
        }
    }

    @Composable
    @Preview
    private fun PreviewWelcome() {
        WelcomeSection()
    }
}
