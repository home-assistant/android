package io.homeassistant.companion.android.common.util.feature

import io.homeassistant.companion.android.common.data.LocalStorage

/**
 * Represents the simplest implementation of [FeatureValue] that is not updatable.
 *
 * This class always returns the default value defined in the associated [FeatureDefinition.defaultValue].
 * It is useful in scenarios where features do not require runtime updates.
 *
 * @param T The type of value associated with the feature.
 *
 * @property feature The [FeatureDefinition] that provides the default value for this feature.
 */
class DefaultFeatureValue<T>(override val feature: FeatureDefinition<T>) : FeatureValue<T> {
    override suspend fun getValue(): T = feature.defaultValue
}

/**
 * A simple implementation of [FeatureValue] that allows the value to be updatable and persisted locally.
 *
 * This class uses a [LocalStorage] instance as a backend to store the feature value.
 * It enforces coverage of all [FeatureDefinition]s by utilizing the `when` operator to ensure
 * that each feature type is explicitly handled.
 *
 * @param T The type of value associated with the feature.
 * @property localStorage An instance of [LocalStorage] used for storing the feature's value.
 * @property feature The [FeatureDefinition] that provides the context and default value for this feature.
 */
class LocalFeatureValue<T>(private val localStorage: LocalStorage, override val feature: FeatureDefinition<T>) : FeatureValue.UpdatableFeatureValue<T>, FeatureValue<T> {
    /*
    We store the key in a property so that if someone initialize a LocalFeatureValue with an anonymous created feature,
    it would throw since a key is based on the class canonicalName.
     */
    private val featureKey = feature.key

    override suspend fun updateValue(newValue: T) {
        when (feature) {
            is FeatureDefinition.BooleanFeatureDefinition -> localStorage.putBoolean(featureKey, newValue as Boolean)
            is FeatureDefinition.StringFeatureDefinition -> localStorage.putString(featureKey, newValue as String)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun getValue(): T {
        val value = when (feature) {
            is FeatureDefinition.BooleanFeatureDefinition -> localStorage.getBooleanOrNull(featureKey) ?: feature.defaultValue
            is FeatureDefinition.StringFeatureDefinition -> localStorage.getString(featureKey) ?: feature.defaultValue
        }
        return value as T
    }
}
