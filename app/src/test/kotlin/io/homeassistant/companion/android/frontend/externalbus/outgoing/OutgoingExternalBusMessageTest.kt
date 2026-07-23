package io.homeassistant.companion.android.frontend.externalbus.outgoing

import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.frontend.addto.ExternalEntityAddToAction
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutgoingExternalBusMessageTest {

    @Test
    fun `Given a result config message when serializing then it generates valid JSON`() {
        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(
            ConfigResultMessage(
                id = 1,
                hasNfc = true,
                canCommissionMatter = true,
                canExportThread = true,
                hasBarCodeScanner = 0,
                canSetupImprov = true,
                appVersion = AppVersion.from("1.0.0 (1)"),
            ),
        )
        assertEquals(
            """{"type":"result","id":1,"success":true,"result":{"hasSettingsScreen":true,"canWriteTag":true,"hasExoPlayer":true,"canCommissionMatter":true,"canImportThreadCredentials":true,"hasAssist":true,"hasBarCodeScanner":0,"canSetupImprov":true,"downloadFileSupported":true,"appVersion":"1.0.0 (1)","hasEntityAddTo":true,"hasAssistSettings":true},"error":null}""",
            json,
        )
    }

    @Test
    fun `Given device without BLE when serializing config then canSetupImprov is false`() {
        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(
            ConfigResultMessage(
                id = 2,
                hasNfc = false,
                canCommissionMatter = false,
                canExportThread = false,
                hasBarCodeScanner = 0,
                canSetupImprov = false,
                appVersion = AppVersion.from("1.0.0 (1)"),
            ),
        )
        assertTrue(json.contains(""""canSetupImprov":false"""))
    }

    @Test
    fun `Given ConfigResult then default values are correct`() {
        val config = ConfigResultMessage.ConfigResult.create(
            hasNfc = false,
            canCommissionMatter = false,
            canExportThread = false,
            hasBarCodeScanner = 0,
            canSetupImprov = true,
            appVersion = AppVersion.from("1.0.0 (1)"),
        )

        assertTrue(config.hasSettingsScreen)
        assertTrue(config.hasExoPlayer)
        assertTrue(config.hasAssist)
        assertTrue(config.canSetupImprov)
        assertTrue(config.downloadFileSupported)
        assertTrue(config.hasEntityAddTo)
        assertTrue(config.hasAssistSettings)
    }

    @Test
    fun `Given EntityAddToActions response when serializing then JSON uses snake_case fields`() {
        val actions = listOf(
            ExternalEntityAddToAction(
                appPayload = "dGVzdA==",
                enabled = true,
                name = "Entity Widget",
                details = null,
                mdiIcon = "mdi:shape",
            ),
        )
        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(EntityAddToActionsResultMessage(id = 20, actions = actions))

        assertEquals(
            """{"type":"result","id":20,"success":true,"result":{"actions":[{"app_payload":"dGVzdA==","enabled":true,"name":"Entity Widget","details":null,"mdi_icon":"mdi:shape"}]},"error":null}""",
            json,
        )
    }
}
