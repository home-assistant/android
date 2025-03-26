package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.util.jacksonObjectMapperForHACore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConversationResponseTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapperForHACore()

    @Test
    fun `deserialize ConversationResponse with missing continueConversation field`() {
        // Arrange
        val json = """
            {
              "response": {
                "speech": {
                  "plain": {
                    "speech": "I can start a timer for you. How long should it be?",
                    "extra_data": null
                  }
                }
              },
              "conversation_id": "01JP0CHJ5YP97SFZAKH1Y6SXNQ"
            }
        """.trimIndent()
        // Act
        val deserializedResponse: ConversationResponse = objectMapper.readValue(json)

        // Assert
        assertEquals("01JP0CHJ5YP97SFZAKH1Y6SXNQ", deserializedResponse.conversationId)
        assertEquals(false, deserializedResponse.continueConversation)
    }

    @Test
    fun `deserialize ConversationResponse with continueConversation field`() {
        // Arrange
        val json = """
            {
              "response": {
                "speech": {
                  "plain": {
                    "speech": "I can start a timer for you. How long should it be?",
                    "extra_data": null
                  }
                }
              },
              "conversation_id": "01JP0CHJ5YP97SFZAKH1Y6SXNQ",
              "continue_conversation": true
            }
        """.trimIndent()
        // Act
        val deserializedResponse: ConversationResponse = objectMapper.readValue(json)

        // Assert
        assertEquals("01JP0CHJ5YP97SFZAKH1Y6SXNQ", deserializedResponse.conversationId)
        assertEquals(true, deserializedResponse.continueConversation)
    }

    @Test
    fun `deserialize ConversationResponse with extra fields`() {
        // Arrange
        val json = """
            {
              "response": {
                "speech": {
                  "plain": {
                    "speech": "I can start a timer for you. How long should it be?",
                    "extra_data": null
                  }
                }
              },
              "conversation_id": "01JP0CHJ5YP97SFZAKH1Y6SXNQ",
              "new_super_field": "empty"
            }
        """.trimIndent()

        // Act
        val deserializedResponse: ConversationResponse = objectMapper.readValue(json)

        // Assert
        assertEquals("01JP0CHJ5YP97SFZAKH1Y6SXNQ", deserializedResponse.conversationId)
    }
}
