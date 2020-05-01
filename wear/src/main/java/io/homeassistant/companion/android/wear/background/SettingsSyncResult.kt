package io.homeassistant.companion.android.wear.background

import io.homeassistant.companion.android.domain.authentication.Session

sealed class SyncResult

class SuccessSyncResult(
    val session: Session,
    val urls: Map<SettingsUrl, String>,
    val ssids: List<String>
) : SyncResult()
object InActiveSessionSyncResult : SyncResult()
object FailedSyncResult : SyncResult()
