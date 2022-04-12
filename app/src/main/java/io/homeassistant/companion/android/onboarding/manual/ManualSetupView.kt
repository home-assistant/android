package io.homeassistant.companion.android.onboarding.manual

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.common.R as commonR

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ManualSetupView(
    onboardingViewModel: OnboardingViewModel,
    connectedClicked: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {

        Text(
            text = stringResource(commonR.string.manual_title),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        )

        Text(
            text = stringResource(id = commonR.string.manual_desc),
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(20.dp)
        )

        TextField(
            value = onboardingViewModel.manualUrl.value,
            onValueChange = { onboardingViewModel.onManualUrlUpdated(it) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            label = { Text(stringResource(id = commonR.string.input_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    connectedClicked()
                }
            )
        )

        Button(
            enabled = onboardingViewModel.manualContinueEnabled,
            onClick = connectedClicked,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(10.dp)
        ) {
            Text(stringResource(commonR.string.connect))
        }
    }
}
