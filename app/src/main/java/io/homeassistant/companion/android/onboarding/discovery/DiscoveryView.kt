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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
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
