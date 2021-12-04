package io.homeassistant.companion.android.onboarding.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationRepositoryImpl
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowInit
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import java.net.URL
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun DiscoveryView(
    onboardingViewModel: OnboardingViewModel,
    whatIsThisClicked: () -> Unit,
    manualSetupClicked: () -> Unit,
    instanceClicked: (instance: HomeAssistantInstance) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth()) {

        TextButton(
            onClick = whatIsThisClicked,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(commonR.string.what_is_this)
            )
        }
        Text(
            text = stringResource(id = commonR.string.select_instance),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(20.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(20.dp)
        ) {
            onboardingViewModel.foundInstances.forEach { instance ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { instanceClicked(instance) })
                ) {
                    Column {
                        Text(
                            text = instance.name,
                            style = TextStyle(
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = instance.url.toString(),
                            style = TextStyle(
                                fontSize = 16.sp
                            )
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.navigate_next),
                        contentDescription = null
                    )
                }
                Divider(Modifier.padding(10.dp))
            }
        }
        TextButton(
            onClick = manualSetupClicked,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(10.dp)
        ) {
            Text(text = stringResource(commonR.string.manual_setup))
        }
    }
}

@Preview
@Composable
fun PreviewDiscoveryView() {
    val viewModel = OnboardingViewModel(HomeAssistantApplication(), object :AuthenticationRepository{
        override suspend fun initiateLoginFlow(): LoginFlowInit {
            TODO("Not yet implemented")
        }

        override suspend fun loginAuthentication(
            flowId: String,
            username: String,
            password: String
        ): LoginFlowCreateEntry {
            TODO("Not yet implemented")
        }

        override suspend fun registerAuthorizationCode(authorizationCode: String) {
            TODO("Not yet implemented")
        }

        override suspend fun retrieveExternalAuthentication(forceRefresh: Boolean): String {
            TODO("Not yet implemented")
        }

        override suspend fun retrieveAccessToken(): String {
            TODO("Not yet implemented")
        }

        override suspend fun revokeSession() {
            TODO("Not yet implemented")
        }

        override suspend fun getSessionState(): SessionState {
            TODO("Not yet implemented")
        }

        override suspend fun buildAuthenticationUrl(callbackUrl: String): URL {
            TODO("Not yet implemented")
        }

        override suspend fun buildBearerToken(): String {
            TODO("Not yet implemented")
        }

        override suspend fun setLockEnabled(enabled: Boolean) {
            TODO("Not yet implemented")
        }

        override suspend fun isLockEnabled(): Boolean {
            TODO("Not yet implemented")
        }
    })
    for (i in 0..5) {
        viewModel.foundInstances.add(
            HomeAssistantInstance(
                "Test Server $i",
                URL("http://localhost_$i:8123"),
                "version_$i"
            )
        )
    }
    DiscoveryView(viewModel, {}, {}) {}
}
