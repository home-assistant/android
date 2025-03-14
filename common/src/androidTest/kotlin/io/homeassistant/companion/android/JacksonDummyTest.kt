package io.homeassistant.companion.android

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Test

private data class DummyData(val name: String)

/**
 * This test is to ensure that jackson is working properly.
 * https://github.com/home-assistant/android/issues/3518
 * If we go above version 2.14 this test is going to fail on API 21 and forbid us to merge the PR.
 */
class JacksonDummyTest {

    @Test
    fun serialize() {
        val data = DummyData("HA")
        val json = jacksonObjectMapper().writeValueAsString(data)

        assertEquals("""{"name":"HA"}""", json)
    }

    @Test
    fun deserialize() {
        val json = """
            {
              "name": "HA"
            }
        """.trimIndent()
        val deserialized: DummyData = jacksonObjectMapper().readValue(json)

        assertEquals("HA", deserialized.name)
    }
}
