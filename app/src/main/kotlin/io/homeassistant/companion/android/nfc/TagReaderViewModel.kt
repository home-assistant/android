package io.homeassistant.companion.android.nfc

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.UrlUtil
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Lower bound on how long the [TagReaderUiState.Scanning] state stays visible. Servers may
 * answer faster than the user can perceive the loading sheet, so we keep it on screen for
 * at least this long before transitioning to [TagReaderUiState.Done].
 */
@VisibleForTesting
internal val MIN_SCANNING_DURATION = 1.5.seconds

/**
 * Drives the NFC / QR tag reader flow.
 *
 * Reads the tag id from the incoming URL, decides whether it can be auto-scanned
 * based on whether it is already present in the allowed tags preference, or requires
 * manual user approval when it is not yet allowed, and exposes the result as [uiState].
 */
@HiltViewModel
class TagReaderViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TagReaderUiState>(TagReaderUiState.Initial)
    val uiState: StateFlow<TagReaderUiState> = _uiState.asStateFlow()

    /**
     * Called by the activity once the launching intent has been parsed. Drives
     * a single tag-handling cycle and updates [uiState] accordingly.
     */
    fun onIntentReceived(url: Uri?, isNfcTag: Boolean) {
        viewModelScope.launch {
            val tagId = UrlUtil.splitNfcTagId(url)
            if (tagId == null) {
                Timber.w("Tag intent had no tag id, isNfcTag=$isNfcTag")
                _uiState.value = TagReaderUiState.Error(errorMessageRes(isNfcTag))
                return@launch
            }
            if (!serverManager.isRegistered()) {
                Timber.w("Tag scanned but no server is registered")
                _uiState.value = TagReaderUiState.Error(errorMessageRes(isNfcTag))
                return@launch
            }
            if (prefsRepository.getAllowedTags().contains(tagId)) {
                scanAndFinish(tagId)
            } else {
                _uiState.value = TagReaderUiState.ApprovingTag(tagId)
            }
        }
    }

    /**
     * Called when the user taps "Allow Once" in the approval bottom sheet. Transitions
     * the sheet into its scanning state and dispatches the tag to all registered servers.
     *
     * Has no effect unless the current state is [TagReaderUiState.ApprovingTag].
     */
    fun onAllowOnce() {
        val current = _uiState.value as? TagReaderUiState.ApprovingTag ?: return
        viewModelScope.launch {
            scanAndFinish(current.tagId)
        }
    }

    /**
     * Called when the user taps "Allow always" in the approval bottom sheet. Persists
     * the tag id so future scans of the same tag skip the
     * approval step, then proceeds with the scan in the same way as [onAllowOnce].
     *
     * Has no effect unless the current state is [TagReaderUiState.ApprovingTag].
     */
    fun onAllowAlways() {
        val current = _uiState.value as? TagReaderUiState.ApprovingTag ?: return
        viewModelScope.launch {
            prefsRepository.addAllowedTag(current.tagId)
            scanAndFinish(current.tagId)
        }
    }

    /**
     * Called once the user-facing error has been dismissed. Transitions to [TagReaderUiState.Done]
     * so the activity can finish.
     */
    fun onErrorAcknowledged() {
        if (_uiState.value is TagReaderUiState.Error) {
            _uiState.value = TagReaderUiState.Done
        }
    }

    /**
     * Called when the user dismisses the approval.
     * Transitions to [TagReaderUiState.Done] so the activity can finish.
     */
    fun onDismissed() {
        _uiState.value = TagReaderUiState.Done
    }

    private suspend fun scanAndFinish(tagId: String) {
        _uiState.value = TagReaderUiState.Scanning
        coroutineScope {
            launch { delay(MIN_SCANNING_DURATION) }
            launch { scanTag(tagId) }
        }
        _uiState.value = TagReaderUiState.Done
    }

    private suspend fun scanTag(tagId: String) {
        coroutineScope {
            serverManager.servers().map { server ->
                async {
                    try {
                        serverManager.integrationRepository(server.id)
                            .scanTag(mapOf("tag_id" to tagId))
                        Timber.d("Tag scanned to HA serverId=${server.id}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Tag not scanned to HA serverId=${server.id}")
                    }
                }
            }.awaitAll()
        }
    }

    private fun errorMessageRes(isNfcTag: Boolean): Int = if (isNfcTag) {
        commonR.string.nfc_processing_tag_error
    } else {
        commonR.string.qrcode_processing_tag_error
    }
}
