package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompressedEntityTest {

    @Test
    fun `CompressedEntityState decoding with empty data`() {
        val rawData = "{}"
        assertEquals(CompressedEntityState(), kotlinJsonMapper.decodeFromString<CompressedEntityState>(rawData))
    }
}
