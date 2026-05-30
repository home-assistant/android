package io.homeassistant.companion.android.data

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Wear-specific bindings for Wear Dashboard tile rendering and actions.
 *
 * Dashboard repository and state cache bindings live in the common [io.homeassistant.companion.android.di.DataModule].
 * Tile renderer and action classes are constructor-injected via Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object DashboardModule
