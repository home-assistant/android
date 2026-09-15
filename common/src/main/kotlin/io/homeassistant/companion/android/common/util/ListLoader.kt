package io.homeassistant.companion.android.common.util

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Runs [loader] and returns its result, falling back to an empty list when the loader returns
 * null or throws. Failures are logged with [description] for context, allowing callers to show
 * partial data instead of failing entirely — useful when loading optional data such as the
 * entity, device, or area registries for a server.
 */
suspend fun <T> loadListOrEmpty(description: String, loader: suspend () -> List<T>?): List<T> = try {
    loader().orEmpty()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.e(e, "Couldn't load $description")
    emptyList()
}
