package io.homeassistant.companion.android.common.data.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CloudPushTransportTest {

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\n"])
    fun `Given a blank URL when creating an endpoint then it throws`(url: String) {
        assertThrows(IllegalArgumentException::class.java) {
            CloudPushTransport.Endpoint(url)
        }
    }

    @Test
    fun `Given a URL when creating an endpoint then it keeps the URL`() {
        assertEquals("https://push.example.com/up1", CloudPushTransport.Endpoint("https://push.example.com/up1").url)
    }
}
