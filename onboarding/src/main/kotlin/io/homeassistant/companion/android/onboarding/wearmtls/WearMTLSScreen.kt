package io.homeassistant.companion.android.onboarding.wearmtls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R

private val MaxTextWidth = MaxButtonWidth

@Composable
internal fun WearMTLSScreen(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    onNext: (certUri: Uri, certPassword: String) -> Unit,
    viewModel: WearMTLSViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WearMTLSScreen(
        onHelpClick = onHelpClick,
        onBackClick = onBackClick,
        onNext = onNext,
        modifier = modifier,
        selectedUri = state.selectedUri,
        selectedFilename = state.selectedFileName,
        currentPassword = state.currentPassword,
        isCertValidated = state.isCertValidated,
        isError = state.showError,
        onPasswordChange = viewModel::onPasswordChanged,
        onFileChange = viewModel::onUriSelected,
    )
}

@Composable
internal fun WearMTLSScreen(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    onNext: (certUri: Uri, certPassword: String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFileChange: (Uri?) -> Unit,
    selectedUri: Uri?,
    selectedFilename: String?,
    currentPassword: String,
    isCertValidated: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick, onBackClick = onBackClick) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) {
        WearMTLSContent(
            modifier = Modifier.padding(it),
            onNext = onNext,
            onPasswordChange = onPasswordChange,
            onFileChange = onFileChange,
            selectedUri = selectedUri,
            selectedFilename = selectedFilename,
            currentPassword = currentPassword,
            isCertValidated = isCertValidated,
            isError = isError,
        )
    }
}

@Composable
private fun WearMTLSContent(
    onNext: (certUri: Uri, certPassword: String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFileChange: (Uri?) -> Unit,
    selectedUri: Uri?,
    selectedFilename: String?,
    currentPassword: String,
    isCertValidated: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HADimens.SPACE4)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        val getFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            // The URI will be null if the user cancelled the file selection.
            // In this case, we don't want to invoke the callback, because that would override
            // the currently selected file (if any).
            if (uri != null) {
                onFileChange(uri)
            }
        }

        Header()

        CertPicker(
            selectedFileName = selectedFilename,
            onSelectFileClicked = {
                getFile.launch("*/*")
            },
            onResetFileSelected = {
                onFileChange(null)
            },
        )

        PasswordTextField(
            currentPassword = currentPassword,
            onPasswordChange = {
                onPasswordChange(it)
            },
            isError = isError,
        )

        Spacer(modifier = Modifier.weight(1f))

        HAAccentButton(
            text = stringResource(commonR.string.wear_mtls_next),
            onClick = {
                onNext(checkNotNull(selectedUri) { "No file selected" }, currentPassword)
            },
            enabled = isCertValidated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HADimens.SPACE6),
        )
    }
}

@Composable
private fun ColumnScope.Header() {
    Image(
        modifier = Modifier.padding(top = HADimens.SPACE6),
        // Use painterResource instead of vector resource for API < 24 since it has gradients
        painter = painterResource(R.drawable.ic_location_secure),
        contentDescription = null,
    )
    Text(
        text = stringResource(id = commonR.string.tls_cert_onboarding_title),
        style = HATextStyle.Headline,
    )
    Text(
        text = stringResource(commonR.string.wear_mtls_content),
        style = HATextStyle.Body,
        modifier = Modifier.widthIn(max = MaxTextWidth),
    )
}

@Composable
private fun CertPicker(selectedFileName: String?, onSelectFileClicked: () -> Unit, onResetFileSelected: () -> Unit) {
    HAAccentButton(
        variant = ButtonVariant.NEUTRAL,
        size = ButtonSize.MEDIUM,
        text = selectedFileName ?: stringResource(commonR.string.select_file),
        maxLines = 1,
        textOverflow = TextOverflow.Ellipsis,
        onClick = onSelectFileClicked,
        prefix = if (selectedFileName == null) {
            {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            null
        },
        suffix = if (selectedFileName != null) {
            {
                IconButton(
                    onClick = onResetFileSelected,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.wear_mtls_deselect_file),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun PasswordTextField(currentPassword: String, onPasswordChange: (String) -> Unit, isError: Boolean) {
    val focusRequester = remember { FocusRequester() }

    HATextField(
        value = currentPassword,
        onValueChange = onPasswordChange,
        label = { Text(text = stringResource(id = commonR.string.password)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        maxLines = 1,
        keyboardActions = KeyboardActions(
            onDone = {
                focusRequester.freeFocus()
                // This is going to hide the keyboard and clear focus on the text field
                defaultKeyboardAction(ImeAction.Done)
            },
        ),
        trailingIcon = {
            if (currentPassword.isNotEmpty()) {
                IconButton(onClick = { onPasswordChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.clear_text),
                    )
                }
            }
        },
        isError = isError,
        supportingText = {
            if (isError) {
                Text(
                    stringResource(commonR.string.wear_mtls_open_error),
                    // TODO probably wrong style and color/token
                    style = HATextStyle.BodyMedium.copy(color = LocalHAColorScheme.current.colorBorderDangerNormal),
                )
            }
        },
    )
}

@HAPreviews
@Composable
private fun WearMTLSScreenPreview() {
    HAThemeForPreview {
        WearMTLSScreen(
            onHelpClick = {},
            onBackClick = {},
            onNext = { _, _ -> },
            onPasswordChange = {},
            onFileChange = {},
            selectedUri = null,
            selectedFilename = null,
            currentPassword = "",
            isCertValidated = true,
            isError = false,
        )
    }
}
