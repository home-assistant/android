package io.homeassistant.companion.android.settings.developer.features

import io.homeassistant.companion.android.common.util.feature.DefaultFeatureValue
import io.homeassistant.companion.android.common.util.feature.FeatureDefinition
import io.homeassistant.companion.android.common.util.feature.FeatureValue
import io.homeassistant.companion.android.common.util.feature.FeatureValuesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class FakeFeatureValuesStore(private val featureValue: Set<FeatureValue<*>>) : FeatureValuesStore {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getFeatureValue(feature: FeatureDefinition<T>): FeatureValue<T> {
        return featureValue.find { it.feature == feature } as FeatureValue<T>
    }

    override fun featuresValue(): Set<FeatureValue<*>> = featureValue

    override fun features(): Set<FeatureDefinition<*>> = featureValue.map { it.feature }.toSet()
}

private class TestableFeatureValue<T>(override val feature: FeatureDefinition<T>) : FeatureValue.UpdatableFeatureValue<T> {
    var value: T = feature.defaultValue

    override suspend fun updateValue(newValue: T) {
        value = newValue
    }

    override suspend fun getValue(): T = value
}

class FeaturesSettingsViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    // Init

    @Test
    fun `Given a feature values store with features when creating viewmodel, then viewStateFlow set with viewState`() = runTest {
        // Given
        val store = FakeFeatureValuesStore(
            setOf(
                DefaultFeatureValue(TestBooleanFeature)
            )
        )
        val expectedViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())

        // When
        val viewModel = FeaturesSettingsViewModel(store)

        // Then
        assertEquals(expectedViewState, viewModel.viewStateFlow.value)
    }

    // FeaturesSettingsInteraction

    @Test
    fun `Given a string feature and the viewModel when invoking onStringFeatureSelected, then viewStateFlow is updated`() = runTest {
        // Given
        val store = FakeFeatureValuesStore(setOf(DefaultFeatureValue(TestStringFeature)))
        val initialViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())
        val feature = initialViewState.features.first() as Feature.StringFeature
        val expectedViewState = initialViewState.copyWith(feature)

        // When
        val viewModel = FeaturesSettingsViewModel(store)
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
        viewModel.onStringFeatureSelected(feature)

        // Then
        assertEquals(expectedViewState, viewModel.viewStateFlow.value)
    }

    @Test
    fun `Given a updatable string feature value and the viewModel when invoking onStringFeatureChanged, then viewStateFlow is updated and the feature value too`() = runTest {
        // Given
        val featureValue = TestableFeatureValue(TestStringFeature)
        val store = FakeFeatureValuesStore(setOf(featureValue))
        val initialViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())
        val featureViewState = initialViewState.features.first() as Feature.StringFeature
        val newValue = "helloWorld"
        val expectedViewState = FeaturesSettingsViewState(listOf(featureViewState.copy(value = newValue)))

        // When
        val viewModel = FeaturesSettingsViewModel(store)
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
        viewModel.onStringFeatureChanged(featureViewState, newValue)

        // Then
        assertEquals(newValue, store.getFeatureValue(TestStringFeature).getValue())
        assertEquals(expectedViewState, viewModel.viewStateFlow.value)
    }

    @Test
    fun `Given a string feature not in the store value and the viewModel when invoking onStringFeatureChanged, then nothing happen`() = runTest {
        // Given
        val store = FakeFeatureValuesStore(emptySet())
        val initialViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())

        // When
        val viewModel = FeaturesSettingsViewModel(store)
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
        viewModel.onStringFeatureChanged(Feature.StringFeature(name = "hello", key = "key", true, value = "value"), "")

        // Then
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
    }

    @Test
    fun `Given a immutable string feature in the store value and the viewModel when invoking onStringFeatureChanged, then nothing happen`() = runTest {
        // Given
        val featureValue = DefaultFeatureValue(TestStringFeature)
        val store = FakeFeatureValuesStore(setOf(featureValue))
        val initialViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())
        val featureViewState = initialViewState.features.first() as Feature.StringFeature

        // When
        val viewModel = FeaturesSettingsViewModel(store)
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
        viewModel.onStringFeatureChanged(featureViewState, "crazy value")

        // Then
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
    }

    @Test
    fun `Given a updatable boolean feature value and the viewModel when invoking onBooleanFeatureChanged, then viewStateFlow is updated and the feature value too`() = runTest {
        // Given
        val featureValue = TestableFeatureValue(TestBooleanFeature)
        val store = FakeFeatureValuesStore(setOf(featureValue))
        val initialViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())
        val featureViewState = initialViewState.features.first() as Feature.BooleanFeature
        val expectedViewState = FeaturesSettingsViewState(listOf(featureViewState.copy(value = !TestBooleanFeature.defaultValue)))

        // When
        val viewModel = FeaturesSettingsViewModel(store)
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
        viewModel.onBooleanFeatureChanged(featureViewState, !TestBooleanFeature.defaultValue)

        // Then
        assertEquals(!TestBooleanFeature.defaultValue, store.getFeatureValue(TestBooleanFeature).getValue())
        assertEquals(expectedViewState, viewModel.viewStateFlow.value)
    }

    @Test
    fun `Given a boolean feature not in the store value and the viewModel when invoking onBooleanFeatureChanged, then nothing happen`() = runTest {
        // Given
        val store = FakeFeatureValuesStore(emptySet())
        val initialViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())

        // When
        val viewModel = FeaturesSettingsViewModel(store)
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
        viewModel.onBooleanFeatureChanged(Feature.BooleanFeature(name = "hello", key = "key", true, value = false), true)

        // Then
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
    }

    @Test
    fun `Given a immutable boolean feature in the store value and the viewModel when invoking onBooleanFeatureChanged, then nothing happen`() = runTest {
        // Given
        val featureValue = DefaultFeatureValue(TestBooleanFeature)
        val store = FakeFeatureValuesStore(setOf(featureValue))
        val initialViewState = FeaturesSettingsViewState.fromFeatureValues(store.featuresValue())
        val featureViewState = initialViewState.features.first() as Feature.BooleanFeature

        // When
        val viewModel = FeaturesSettingsViewModel(store)
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
        viewModel.onBooleanFeatureChanged(featureViewState, !TestBooleanFeature.defaultValue)

        // Then
        assertEquals(initialViewState, viewModel.viewStateFlow.value)
    }

    // TODO Proper commits
    // Proper PR with screenshots
}
