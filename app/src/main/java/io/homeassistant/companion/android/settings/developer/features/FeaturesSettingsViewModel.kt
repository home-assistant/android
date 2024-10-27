package io.homeassistant.companion.android.settings.developer.features

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.util.feature.FeatureValue
import io.homeassistant.companion.android.common.util.feature.FeatureValuesStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

@HiltViewModel
internal class FeaturesSettingsViewModel @Inject constructor(private val featureValuesStore: FeatureValuesStore) : ViewModel(), FeaturesSettingsInteraction {
    private val viewStateMutableFlow = MutableStateFlow(FeaturesSettingsViewState.empty())
    val viewStateFlow = viewStateMutableFlow.asStateFlow()

    init {
        viewModelScope.launch {
            updateViewState()
        }
    }

    private companion object {
        private const val TAG = "FeaturesSettingsVM"
    }

    override fun onBooleanFeatureChanged(feature: Feature.BooleanFeature, newValue: Boolean) {
        onFeatureChanged(feature, newValue)
    }

    override fun onStringFeatureChanged(feature: Feature.StringFeature, newValue: String) {
        onFeatureChanged(feature, newValue)
    }

    override fun onStringFeatureSelected(feature: Feature.StringFeature?) {
        viewStateMutableFlow.getAndUpdate { it.copyWith(feature) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> onFeatureChanged(feature: Feature, newValue: T) {
        viewModelScope.launch {
            val featureValue = featureValuesStore.featuresValue().find { it.feature.key == feature.key }
            if (featureValue != null) {
                if (featureValue.isFeatureUpdatable()) {
                    (featureValue as FeatureValue.UpdatableFeatureValue<T>).updateValue(newValue)
                    updateViewState()
                } else {
                    Log.w(TAG, "${featureValue.feature} not updatable, ignoring the new value")
                }
            } else {
                Log.w(TAG, "${feature.name} not found in the value store, ignoring the new value")
            }
        }
    }

    private suspend fun updateViewState() {
        viewStateMutableFlow.value = FeaturesSettingsViewState.fromFeatureValues(featureValuesStore.featuresValue())
    }
}
