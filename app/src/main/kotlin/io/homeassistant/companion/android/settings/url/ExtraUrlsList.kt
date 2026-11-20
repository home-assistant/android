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
     *   They are stored as the user typed them (for example without a trailing slash), so they are
     *   normalized the same way as [input] before comparing.
     */
    fun add(current: List<String>, input: String, known: List<String> = emptyList()): AddExtraUrlResult {
        val normalized = normalize(input) ?: return AddExtraUrlResult.Invalid
        val alreadyUsed = (current + known).mapNotNull(::normalize)
        return if (normalized in alreadyUsed) {
            AddExtraUrlResult.Duplicate
        } else {
            AddExtraUrlResult.Added(current + normalized)
        }
    }

    /** @return [url] reduced to scheme, host and port, or `null` when it is not a valid URL. */
    private fun normalize(url: String): String? {
        return try {
            UrlUtil.extractBaseUrl(url.trim())
        } catch (_: MalformedHttpUrlException) {
            null
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
