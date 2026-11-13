package io.homeassistant.companion.android.settings.url

import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.util.UrlUtil

/** Result of trying to add a URL to the list of extra URLs. */
sealed interface AddExtraUrlResult {
    data class Added(val urls: List<String>) : AddExtraUrlResult
    data object Invalid : AddExtraUrlResult
    data object Duplicate : AddExtraUrlResult
}

/**
 * Pure list operations for the ordered extra URLs of a server, kept separate from the
 * ViewModel so they can be unit tested.
 */
internal object ExtraUrlsList {

    /**
     * Normalizes [input] to scheme, host and port and appends it to [current].
     *
     * @param known URLs already used by the server (external, internal) that must not be repeated.
     */
    fun add(current: List<String>, input: String, known: List<String> = emptyList()): AddExtraUrlResult {
        val normalized = try {
            UrlUtil.extractBaseUrl(input.trim())
        } catch (_: MalformedHttpUrlException) {
            return AddExtraUrlResult.Invalid
        }
        return if (normalized in current || normalized in known) {
            AddExtraUrlResult.Duplicate
        } else {
            AddExtraUrlResult.Added(current + normalized)
        }
    }

    fun remove(current: List<String>, index: Int): List<String> {
        return if (index in current.indices) current.filterIndexed { i, _ -> i != index } else current
    }

    /** Moves the entry at [index] by [offset] positions (-1 up, +1 down), ignoring out of range moves. */
    fun move(current: List<String>, index: Int, offset: Int): List<String> {
        val target = index + offset
        if (index !in current.indices || target !in current.indices) return current
        return current.toMutableList().apply { add(target, removeAt(index)) }
    }
}
