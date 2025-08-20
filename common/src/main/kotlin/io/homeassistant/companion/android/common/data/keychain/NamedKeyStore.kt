package io.homeassistant.companion.android.common.data.keychain

import javax.inject.Qualifier

/**
 * Qualifier for the [KeyChainRepository] used to select the key store.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedKeyStore
