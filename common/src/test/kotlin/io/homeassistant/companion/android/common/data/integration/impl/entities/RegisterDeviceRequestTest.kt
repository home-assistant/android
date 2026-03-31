package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class RegisterDeviceRequestTest {

    @Test
    fun `Given a valid RegisterDeviceRequest when serializing it then it creates a valid JSON`() {
        assertEquals(
            """{"app_id":"1","app_name":"2","app_version":"3 (3)","device_name":"4","manufacturer":"5","model":"6","os_name":"7","os_version":"8","supports_encryption":true,"app_data":{"1":"2","2":1,"3":"token"},"device_id":"3"}""",
            kotlinJsonMapper.encodeToString(
                RegisterDeviceRequest(
                    appId = "1",
                    appName = "2",
                    appVersion = AppVersion.from("3", 3),
                    deviceName = "4",
                    manufacturer = "5",
                    model = "6",
                    osName = "7",
                    osVersion = "8",
                    supportsEncryption = true,
                    appData = mapOf("1" to "2", "2" to 1, "3" to MessagingToken("token")),
                    deviceId = "3",
                ),
            ),
        )
        assertEquals(
            """{}""",
            kotlinJsonMapper.encodeToString(
                RegisterDeviceRequest(),
            ),
        )
    }

    @Test
    fun `Given a valid JSON when deserializing it then it creates a valid RegisterDeviceRequest`() {
        assertEquals(
            RegisterDeviceRequest(
                appId = "1",
                appName = "2",
                appVersion = AppVersion.from("3", 3),
                deviceName = "4",
                manufacturer = "5",
                model = "6",
                osName = "7",
                osVersion = "8",
                supportsEncryption = true,
                appData = mapOf("1" to "2", "2" to 1),
                deviceId = "3",
            ),
            kotlinJsonMapper.decodeFromString<RegisterDeviceRequest>("""{"app_id":"1","app_name":"2","app_version":"3 (3)","device_name":"4","manufacturer":"5","model":"6","os_name":"7","os_version":"8","supports_encryption":true,"app_data":{"1":"2","2":1},"device_id":"3"}"""),
        )
        assertEquals(
            RegisterDeviceRequest(),
            kotlinJsonMapper.decodeFromString<RegisterDeviceRequest>("""{}"""),
        )
    }
}
