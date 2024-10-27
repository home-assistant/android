package io.homeassistant.companion.android.common.util.feature

import javax.inject.Inject

/**
 * An implementation of [FeatureValuesStore] that manages a collection of feature values.
 *
 * This class is designed to be injected via dependency injection and created through
 * dependency injection, allowing features to be defined and accessed from anywhere within
 * the application. It maintains a set of feature values, ensuring that all defined features
 * are retrievable and uniquely identifiable.
 *
 * @property featureValues A set of [FeatureValue] instances, representing the feature values
 * defined in the application.
 *
 * The implementation lazily initializes a collection of unique feature definitions based on the
 * provided feature values, allowing for efficient retrieval of features.
 *
 * ## Functionality
 * - **Feature Value Retrieval**: The `getFeatureValue` method allows for the retrieval of a
 *   specific feature value based on its corresponding [FeatureDefinition]. If the feature is not
 *   found, [IllegalStateException] is thrown.
 *
 * - **Feature Listing**: The `features` method provides a set of all feature definitions available
 *   in the store, allowing developers to easily access and display the features defined in the
 *   application.
 *
 * @throws IllegalStateException If a requested feature is not found in the store during retrieval.
 */
internal class FeaturesStoreImpl @Inject constructor(private val featureValues: Set<@JvmSuppressWildcards FeatureValue<*>>) : FeatureValuesStore {

    private val featureDefinitions by lazy { featureValues.map(FeatureValue<*>::feature).toSet() }

    @Suppress("UNCHECKED_CAST")
    private fun <T> findFeatureValue(feature: FeatureDefinition<T>): FeatureValue<T> =
        checkNotNull(featureValues.find { it.feature == feature }) { "Feature ${feature.featureName} not found in the store" } as FeatureValue<T>

    override fun <T> getFeatureValue(feature: FeatureDefinition<T>): FeatureValue<T> = findFeatureValue(feature)
    override fun featuresValue(): Set<FeatureValue<*>> = featureValues

    override fun features(): Set<FeatureDefinition<*>> = featureDefinitions
}
