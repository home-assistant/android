package io.homeassistant.companion.android.onboarding.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.OnboardingHeaderView
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun DiscoveryView(
    onboardingViewModel: OnboardingViewModel,
    manualSetupClicked: () -> Unit,
    instanceClicked: (instance: HomeAssistantInstance) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(16.dp)
    ) {
        OnboardingHeaderView(
            icon = CommunityMaterial.Icon2.cmd_home_search,
            title = stringResource(id = commonR.string.select_instance)
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.25f)
                .padding(vertical = 16.dp)
                .height(2.dp)
                .align(Alignment.CenterHorizontally)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(onboardingViewModel.foundInstances.size, { onboardingViewModel.foundInstances[it].url }) { index ->
                val instance = onboardingViewModel.foundInstances[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(onClick = { instanceClicked(instance) })
                ) {
                    Column {
                        Text(instance.name)
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                            Text(
                                text = instance.url.toString(),
                                fontSize = 14.sp
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.navigate_next),
                        contentDescription = null
                    )
                }
                Divider(Modifier.padding(8.dp))
            }
        }
        TextButton(
            onClick = manualSetupClicked,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
        ) {
            Text(text = stringResource(commonR.string.manual_setup))
        }
    }
}
