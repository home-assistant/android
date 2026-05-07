package io.homeassistant.companion.android.frontend.externalbus.outgoing

import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class OutgoingExternalBusMessageTest {

    @Test
    fun `Given a result config message when serializing then it generates valid JSON`() {
        val json = frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(
            ResultMessage.config(
                id = 1,
                config = ConfigResult.create(
                    hasNfc = true,
                    canCommissionMatter = true,
                    canExportThread = true,
                    hasBarCodeScanner = 0,
                    canSetupImprov = true,
                    appVersion = AppVersion.from("1.0.0 (1)"),
                ),
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
            ResultMessage.config(
                id = 2,
                config = ConfigResult.create(
                    hasNfc = false,
                    canCommissionMatter = false,
                    canExportThread = false,
                    hasBarCodeScanner = 0,
                    canSetupImprov = false,
                    appVersion = AppVersion.from("1.0.0 (1)"),
                ),
            ),
        )
        assertTrue(json.contains(""""canSetupImprov":false"""))
    }

    @Test
    fun `Given ConfigResult then default values are correct`() {
        val config = ConfigResult.create(
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
    fun `Given canSetupImprov passed false to create then ConfigResult exposes canSetupImprov false`() {
        val config = ConfigResult.create(
            hasNfc = false,
            canCommissionMatter = false,
            canExportThread = false,
            hasBarCodeScanner = 0,
            canSetupImprov = false,
            appVersion = AppVersion.from("1.0.0 (1)"),
        )

        assertFalse(config.canSetupImprov)
    }
}
