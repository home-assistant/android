package io.homeassistant.companion.android.common.data.integration

import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.MessagingToken
import javax.inject.Qualifier

/**
 * The registration of this device with one Home Assistant server.
 *
 * [IntegrationRepository.updateRegistration] also takes partial updates, where a `null` property
 * leaves what is already registered unchanged instead of clearing it.
 * [IntegrationRepository.getRegistration] resolves every property from what is stored.
 *
 * @property pushToken Token the server sends along to the cloud push transport. On a partial update
 * it is only applied when it belongs to the effective [cloudPush] transport, so a refreshed
 * messaging token cannot replace the token of an already registered [CloudPushTransport.Endpoint].
 * @property cloudPush Where the server sends notifications while it cannot reach the device over
 * the WebSocket channel. Pass [CloudPushTransport.Firebase] to drop a registered endpoint.
 */
data class DeviceRegistration(
    val appVersion: AppVersion? = null,
    val deviceName: String? = null,
    var pushToken: MessagingToken? = null,
    var pushWebsocket: Boolean = true,
    val cloudPush: CloudPushTransport? = null,
)

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PushWebsocketSupport
