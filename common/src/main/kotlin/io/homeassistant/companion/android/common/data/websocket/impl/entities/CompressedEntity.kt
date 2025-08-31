@file:OptIn(ExperimentalSerializationApi::class)

package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.util.MapAnySerializer
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.round
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Represents a single event emitted in a `subscribe_entities` websocket subscription. One event can
 * contain state changes for multiple entities; properties map them as entity id -> state.
 */
@Serializable
data class CompressedStateChangedEvent(
    @JsonNames("a")
    val added: Map<String, CompressedEntityState>? = null,
    @JsonNames("c")
    val changed: Map<String, CompressedStateDiff>? = null,
    @JsonNames("r")
    val removed: List<String>? = null,
)

/**
 * Describes the difference in an [Entity] state in a `subscribe_entities` websocket subscription.
 * It will only include properties that have been changed, values that haven't changed will not be
 * set (in Kotlin: `null`). Apply it to an existing Entity to get the new state.
 */
@Serializable
data class CompressedStateDiff(
    @JsonNames("+")
    val plus: CompressedEntityState? = null,
    @JsonNames("-")
    val minus: CompressedEntityRemoved? = null,
)

/**
 * A compressed version of [Entity] used for additions or changes in the entity's state in a
 * `subscribe_entities` websocket subscription.
 */
@Serializable
data class CompressedEntityState(
    @JsonNames("s")
    val state: String? = null,
    @Serializable(with = MapAnySerializer::class)
    @JsonNames("a")
    val attributes: Map<String, @Polymorphic Any?> = emptyMap(),
    @JsonNames("lc")
    val lastChanged: Double? = null,
    @JsonNames("lu")
    val lastUpdated: Double? = null,
) {
    /**
     * Convert a compressed entity state to a normal [Entity]. This function can be used for new
     * entities that are delivered in a compressed format.
     */
    fun toEntity(entityId: String): Entity {
        return Entity(
            entityId = entityId,
            state = checkNotNull(state) { "State must not be null" },
            attributes = attributes,
            lastChanged = LocalDateTime.ofEpochSecond(
                round(
                    checkNotNull(lastChanged) {
                        "lastChanged must not be null"
                    },
                ).toLong(),
                0,
                ZoneOffset.UTC,
            ),
            lastUpdated = LocalDateTime.ofEpochSecond(
                if (lastUpdated != null) {
                    round(lastUpdated * 1000).toLong()
                } else {
                    round(lastChanged * 1000).toLong()
                },
                0,
                ZoneOffset.UTC,
            ),
        )
    }
}

/**
 * A compressed version of [Entity] used for removed properties from the entity's state in a
 * `subscribe_entities` websocket subscription. Only attributes are expected to be removed.
 */
@Serializable
data class CompressedEntityRemoved(
    @JsonNames("a")
    val attributes: List<String>? = null,
)
