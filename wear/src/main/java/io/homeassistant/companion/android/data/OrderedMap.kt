package io.homeassistant.companion.android.data

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.ScalingLazyListItemScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope

/**
 * A map that has an associated ordered list used to iterate over the map in a specific order.
 */
class OrderedMap<K : Any, V>(
    items: Map<K, V>,
    val orderedKeys: List<K>
) : Map<K, V> by items

private val emptyOrderedMap = OrderedMap<Any, Nothing>(emptyMap(), emptyList())

/**
 * Returns an empty read-only ordered map of specified type.
 * @see [emptyMap]
 */
fun <K : Any, V> emptyOrderedMap(): OrderedMap<K, V> =
    @Suppress("UNCHECKED_CAST")
    (emptyOrderedMap as OrderedMap<K, V>)

/**
 * Returns a new read-only ordered map, mapping only the specified key to the specified value.
 */
fun <K : Any, V> orderedMapOf(pair: Pair<K, V>): OrderedMap<K, V> = OrderedMap(
    mapOf(pair),
    orderedKeys = listOf(pair.first)
)

/**
 * Adds an [OrderedMap] of items to the view.
 * @param itemsMap - the map of items to add.
 * @param key - a factory of stable keys for the items.
 * Type of the key should be saveable via Bundle on Android.
 * If null is passed the position in the ordered keys list will represent the key.
 * By default, the map key is used.
 * @param itemContent - the content displayed by a single item.
 */
inline fun <K : Any, V> ScalingLazyListScope.items(
    itemsMap: OrderedMap<K, V>,
    noinline key: ((mapKey: K) -> Any)? = { it },
    crossinline itemContent: @Composable ScalingLazyListItemScope.(Pair<K, V>) -> Unit
) = items(
    count = itemsMap.orderedKeys.size,
    key = if (key != null) { index: Int -> key(itemsMap.orderedKeys[index]) } else null
) { index ->
    val mapKey = itemsMap.orderedKeys[index]
    val mapValue = itemsMap[mapKey]
    mapValue?.let {
        itemContent(mapKey to mapValue)
    }
}
