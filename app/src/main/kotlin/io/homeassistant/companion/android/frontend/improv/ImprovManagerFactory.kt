package io.homeassistant.companion.android.frontend.improv

import com.wifi.improv.ImprovManager
import com.wifi.improv.ImprovManagerCallback

/**
 * Creates [ImprovManager] instances on demand for [ImprovRepositoryImpl].
 *
 * Exists so the repository does not have to hold an Android `Context` — the factory closes over
 * the application context at the Hilt provision site, leaving the repository fully unit-testable
 * with a mock factory.
 */
fun interface ImprovManagerFactory {
    fun create(callback: ImprovManagerCallback): ImprovManager
}
