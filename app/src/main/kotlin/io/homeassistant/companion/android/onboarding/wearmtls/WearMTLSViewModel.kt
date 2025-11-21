package io.homeassistant.companion.android.onboarding.wearmtls

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.security.KeyStore
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@VisibleForTesting
internal val passwordValidationDebounce = 500.milliseconds

internal data class WearMTLSUiState(
    val selectedUri: Uri? = null,
    val selectedFileName: String? = null,
    val currentPassword: String = "",
    val isCertValidated: Boolean = false,
    val showError: Boolean = false,
)

private enum class PasswordState {
    // Password hasn't been validated yet
    UNKNOWN,
    VALID,
    INVALID,
}

@HiltViewModel
internal class WearMTLSViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val contentResolver: ContentResolver = context.contentResolver

    private val _uiState = MutableStateFlow(WearMTLSUiState())
    val uiState: StateFlow<WearMTLSUiState> = _uiState.asStateFlow()

    private var passwordValidationJob: Job? = null

    fun onUriSelected(uri: Uri?) {
        _uiState.update { it.copy(selectedUri = uri, selectedFileName = null) }
        if (uri != null) {
            viewModelScope.launch {
                val filename = getFilename(uri)
                _uiState.update { it.copy(selectedFileName = filename) }
                triggerPasswordValidation()
            }
        } else {
            triggerPasswordValidation()
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(currentPassword = password, isCertValidated = false) }
        triggerPasswordValidation()
    }

    private fun triggerPasswordValidation() {
        passwordValidationJob?.cancel()
        passwordValidationJob = viewModelScope.launch {
            val currentSelectedUri = _uiState.value.selectedUri
            val currentPasswordValue = _uiState.value.currentPassword

            val passwordState = if (currentSelectedUri != null && currentPasswordValue.isNotEmpty()) {
                delay(passwordValidationDebounce)
                val isValid = validatePassword(currentSelectedUri, currentPasswordValue)
                if (isValid) PasswordState.VALID else PasswordState.INVALID
            } else {
                PasswordState.UNKNOWN
            }

            _uiState.update {
                it.copy(
                    showError = passwordState == PasswordState.INVALID,
                    isCertValidated = passwordState == PasswordState.VALID,
                )
            }
        }
    }

    private suspend fun getFilename(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                cursor.takeIf { it.moveToFirst() }?.runCatching {
                    val displayNameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    cursor.getString(displayNameIndex)
                }?.onFailure { Timber.e(it, "Error getting filename from URI.") }?.getOrNull()
            }
        }
    }

    private suspend fun validatePassword(selectedUri: Uri, currentPasswordVal: String): Boolean {
        if (currentPasswordVal.isEmpty()) return false
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(selectedUri)?.buffered()?.use { inputStream ->
                    loadAndVerifyKeystore(inputStream, currentPasswordVal.toCharArray())
                } ?: false
            } catch (e: Exception) {
                Timber.e(e, "Error opening or reading certificate file for validation.")
                false
            }
        }
    }

    private fun loadAndVerifyKeystore(inputStream: InputStream, passwordChars: CharArray): Boolean {
        return try {
            val keystore = KeyStore.getInstance("PKCS12")
            keystore.load(inputStream, passwordChars)
            true
        } catch (e: Exception) {
            Timber.w(e, "Impossible to load TLS client certificate, password might be wrong or cert is invalid.")
            false
        }
    }
}
