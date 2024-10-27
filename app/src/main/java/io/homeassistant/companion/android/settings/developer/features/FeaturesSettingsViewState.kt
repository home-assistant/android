package io.homeassistant.companion.android.settings.developer.features

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import io.homeassistant.companion.android.common.util.feature.FeatureDefinition
import io.homeassistant.companion.android.common.util.feature.FeatureValue
import kotlinx.parcelize.Parcelize

internal sealed interface Feature : Parcelable {
    val name: String

    // Unique identifier of a feature
    val key: String
    val isUpdatable: Boolean

    @Parcelize
    data class BooleanFeature(override val name: String, override val key: String, override val isUpdatable: Boolean, val value: Boolean) : Feature

    @Parcelize
    data class StringFeature(override val name: String, override val key: String, override val isUpdatable: Boolean, val value: String) : Feature
}

@Immutable
@Parcelize
internal data class FeaturesSettingsViewState(val features: List<Feature>, val selectedStringFeatures: Feature.StringFeature? = null) : Parcelable {
    fun copyWith(selectedStringFeatures: Feature.StringFeature?): FeaturesSettingsViewState {
        return copy(selectedStringFeatures = selectedStringFeatures)
    }

    companion object {
        suspend fun fromFeatureValues(featuresValue: Set<FeatureValue<*>>): FeaturesSettingsViewState {
            val features = featuresValue.map { featureValue ->
                val definition = featureValue.feature
                when (definition) {
                    is FeatureDefinition.BooleanFeatureDefinition ->
                        Feature.BooleanFeature(definition.featureName, definition.key, featureValue.isFeatureUpdatable(), featureValue.getValue() as Boolean)
                    is FeatureDefinition.StringFeatureDefinition ->
                        Feature.StringFeature(definition.featureName, definition.key, featureValue.isFeatureUpdatable(), featureValue.getValue() as String)
                }
            }

            return FeaturesSettingsViewState(features)
        }

        fun empty() = FeaturesSettingsViewState(emptyList())
    }
}
