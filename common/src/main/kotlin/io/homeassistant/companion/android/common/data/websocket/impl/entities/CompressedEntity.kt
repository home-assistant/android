package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.homeassistant.companion.android.common.data.integration.Entity
import java.util.Calendar
import kotlin.math.round

/**
 * Represents a single event emitted in a `subscribe_entities` websocket subscription. One event can
 * contain state changes for multiple entities; properties map them as entity id -> state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CompressedStateChangedEvent(
    @JsonProperty("a")
    val added: Map<String, CompressedEntityState>?,
    @JsonProperty("c")
    val changed: Map<String, CompressedStateDiff>?,
    @JsonProperty("r")
    val removed: List<String>?
)

/**
 * Describes the difference in an [Entity] state in a `subscribe_entities` websocket subscription.
 * It will only include properties that have been changed, values that haven't changed will not be
 * set (in Kotlin: `null`). Apply it to an existing Entity to get the new state.
 */
data class CompressedStateDiff(
    @JsonProperty("+")
    val plus: CompressedEntityState?,
    @JsonProperty("-")
    val minus: CompressedEntityRemoved?
)

/**
 * A compressed version of [Entity] used for additions or changes in the entity's state in a
 * `subscribe_entities` websocket subscription.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CompressedEntityState(
    @JsonProperty("s")
    val state: String?,
    @JsonProperty("a")
    val attributes: Map<String, Any>?,
    @JsonProperty("lc")
    val lastChanged: Double?,
    @JsonProperty("lu")
    val lastUpdated: Double?,
    @JsonProperty("c")
    val context: Any?
) {
    /**
     * Convert a compressed entity state to a normal [Entity]. This function can be used for new
     * entities that are delivered in a compressed format.
     */
    fun toEntity(entityId: String): Entity<Map<String, Any>> {
        return Entity(
            entityId = entityId,
            state = state!!,
            attributes = attributes ?: mapOf(),
            lastChanged = Calendar.getInstance().apply { timeInMillis = round(lastChanged!! * 1000).toLong() },
            lastUpdated = Calendar.getInstance().apply {
                timeInMillis =
                    if (lastUpdated != null) {
                        round(lastUpdated * 1000).toLong()
                    } else {
                        round(lastChanged!! * 1000).toLong()
                    }
            },
            context =
            if (context is String) {
                mapOf("id" to context, "parent_id" to null, "user_id" to null)
            } else {
                context as? Map<String, Any?>
            }
        )
    }
}

/**
 * A compressed version of [Entity] used for removed properties from the entity's state in a
 * `subscribe_entities` websocket subscription. Only attributes are expected to be removed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CompressedEntityRemoved(
    @JsonProperty("a")
    val attributes: List<String>?
)
