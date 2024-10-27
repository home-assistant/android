package io.homeassistant.companion.android.common.util.feature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FeaturesStoreImplTest {

    @Test
    fun `Given a feature definition in FeatureStores when retrieving its value, then the feature value should be returned`() {
        // Given
        val expectedFeatureValue = DefaultFeatureValue(TestBooleanFeature)
        val store = FeaturesStoreImpl(setOf(expectedFeatureValue))

        // When
        val featureValue = store.getFeatureValue(TestBooleanFeature)

        // Then
        assertEquals(expectedFeatureValue, featureValue)
    }

    @Test
    fun `Given multiple features definitions in FeatureStores when retrieving its value, then the feature value should be returned`() {
        // Given
        val expectedBooleanFeatureValue = DefaultFeatureValue(TestBooleanFeature)
        val expectedStringFeatureValue = DefaultFeatureValue(TestStringFeature)
        val store = FeaturesStoreImpl(setOf(expectedBooleanFeatureValue, expectedStringFeatureValue))

        // When
        val featureStringValue = store.getFeatureValue(TestStringFeature)
        val featureBooleanValue = store.getFeatureValue(TestBooleanFeature)

        // Then
        assertEquals(expectedStringFeatureValue, featureStringValue)
        assertEquals(expectedBooleanFeatureValue, featureBooleanValue)
    }

    @Test
    fun `Given multiple features definitions in FeatureStores when retrieving features, then the feature definition set should be returned`() {
        // Given
        val expectedBooleanFeatureValue = DefaultFeatureValue(TestBooleanFeature)
        val expectedStringFeatureValue = DefaultFeatureValue(TestStringFeature)
        val expectedFeatures = setOf(TestBooleanFeature, TestStringFeature)
        val store = FeaturesStoreImpl(setOf(expectedBooleanFeatureValue, expectedStringFeatureValue))

        // When
        val features = store.features()

        // Then
        assertIterableEquals(expectedFeatures, features)
    }

    @Test
    fun `Given a feature definition not in FeatureStores when retrieving its feature value, then throw an exception`() {
        // Given
        val expectedStringFeatureValue = DefaultFeatureValue(TestStringFeature)
        val store = FeaturesStoreImpl(setOf(expectedStringFeatureValue))

        // When and then
        assertThrows<IllegalStateException>("Feature ${TestBooleanFeature.featureName} not found in the store") { store.getFeatureValue(TestBooleanFeature) }
    }
}
