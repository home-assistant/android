package io.homeassistant.companion.android.onboarding.integration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.OnboardingHeaderView
import io.homeassistant.companion.android.onboarding.OnboardingScreen
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.util.compose.screenWidth

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MobileAppIntegrationView(
    onboardingViewModel: OnboardingViewModel,
    openPrivacyPolicy: () -> Unit,
    onLocationTrackingChanged: (Boolean) -> Unit,
    onSelectTLSCertificateClicked: () -> Unit,
    onCheckPassword: (String) -> Unit,
    onFinishClicked: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    OnboardingScreen {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .weight(1f),
        ) {
            OnboardingHeaderView(
                icon = if (onboardingViewModel.deviceIsWatch) {
                    CommunityMaterial.Icon3.cmd_watch
                } else if (screenWidth() >= 600.dp) {
                    CommunityMaterial.Icon3.cmd_tablet
                } else {
                    CommunityMaterial.Icon.cmd_cellphone
                },
                title = stringResource(id = commonR.string.connect_to_home_assistant),
            )

            TextField(
                value = onboardingViewModel.deviceName.value,
                onValueChange = { onboardingViewModel.onDeviceNameUpdated(it) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                label = { Text(stringResource(id = commonR.string.device_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    },
                ),
            )
            if (onboardingViewModel.locationTrackingPossible.value) {
                Row {
                    Text(
                        text = stringResource(commonR.string.enable_location_tracking),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .weight(1f),
                    )
                    Switch(
                        checked = onboardingViewModel.locationTrackingEnabled,
                        onCheckedChange = onLocationTrackingChanged,
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb),
                        ),
                    )
                }
                Text(
                    text = stringResource(id = commonR.string.enable_location_tracking_description),
                    fontWeight = FontWeight.Light,
                )
            }
            if (onboardingViewModel.deviceIsWatch && onboardingViewModel.mayRequireTlsClientCertificate) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = commonR.string.tls_cert_onboarding_title),
                    style = MaterialTheme.typography.h6,
                )
                Text(
                    text = stringResource(id = commonR.string.tls_cert_onboarding_description),
                    fontWeight = FontWeight.Light,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(onClick = onSelectTLSCertificateClicked) {
                        Text(text = stringResource(id = commonR.string.select_file))
                    }
                    Text(
                        text = onboardingViewModel.tlsClientCertificateFilename,
                        modifier = Modifier
                            .align(Alignment.CenterVertically),
                    )
                }
                if (onboardingViewModel.tlsClientCertificateUri != null) {
                    TextField(
                        value = onboardingViewModel.tlsClientCertificatePassword,
                        onValueChange = {
                            onboardingViewModel.tlsClientCertificatePassword = it
                            onCheckPassword(onboardingViewModel.tlsClientCertificatePassword)
                        },
                        label = { Text(text = stringResource(id = commonR.string.password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                            },
                        ),
                        trailingIcon = {
                            if (onboardingViewModel.tlsClientCertificatePasswordCorrect) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    tint = colorResource(commonR.color.colorOnBackground),
                                    contentDescription = stringResource(id = commonR.string.password_correct),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    tint = colorResource(commonR.color.colorWarning),
                                    contentDescription = stringResource(id = commonR.string.password_incorrect),
                                )
                            }
                        },
                        isError = !onboardingViewModel.tlsClientCertificatePasswordCorrect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TextButton(onClick = openPrivacyPolicy) {
                Text(stringResource(id = commonR.string.privacy_url))
            }
        }

        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onFinishClicked,
                enabled =
                !onboardingViewModel.deviceIsWatch ||
                    onboardingViewModel.tlsClientCertificateUri == null ||
                    onboardingViewModel.tlsClientCertificatePasswordCorrect,
            ) {
                Text(stringResource(id = commonR.string.continue_connect))
            }
        }
    }
}
