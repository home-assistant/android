package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.data.integration.Entity

/**
 * The API layer's name for [Entity], which is already the wire format.
 *
 * A separate response type would duplicate [Entity]'s serializer, which tolerates integrations
 * sending a non-string `state`. Being an alias it adds no type separation: the compiler treats
 * the two as the same type.
 */
internal typealias EntityResponse = Entity
