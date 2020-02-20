package io.homeassistant.companion.android.data.integration

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import io.homeassistant.companion.android.data.integration.entities.IntegrationResponse

class IntegrationDeserializer<T> constructor() : StdDeserializer<IntegrationResponse<T>>(IntegrationResponse::class.java), ContextualDeserializer {

    constructor(targetClass: Class<*>) : this() {
        this.targetClass = targetClass
    }

    private lateinit var targetClass: Class<*>

    override fun createContextual(
        ctxt: DeserializationContext,
        property: BeanProperty?
    ): JsonDeserializer<*> {
        val javaType = if (property != null) {
            property.type
        } else {
            ctxt.contextualType
        }

        targetClass = javaType.bindings.getBoundType(0).rawClass

        return IntegrationDeserializer<T>(targetClass)
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): IntegrationResponse<T> {

        val node = p.codec.readTree<TreeNode>(p)
        val body = if (node.get("encrypted")?.traverse()?.getValueAsBoolean(false) == true) {
            TODO("Implement decryption")
        } else {
            ctxt.readValue(p, targetClass)
        }

        @Suppress("UNCHECKED_CAST")
        return IntegrationResponse(false, body as T)
    }
}
