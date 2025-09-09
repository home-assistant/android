package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

class MessagingTokenTest {

    @BeforeEach
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @Test
    fun `Given JSON when deserialize to MessagingToken then it works`() {
        val source = "\"token\""
        val messagingToken = kotlinJsonMapper.decodeFromString<MessagingToken>(source)
        assertEquals("token", messagingToken.value)
    }

    @Test
    fun `Given MessagingToken when serialize to JSON then it works`() {
        val source = MessagingToken("token")
        val json = kotlinJsonMapper.encodeToString(source)
        assertEquals("\"token\"", json)
    }
}
