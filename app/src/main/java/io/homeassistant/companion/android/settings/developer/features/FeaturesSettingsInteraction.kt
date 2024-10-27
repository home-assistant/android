package io.homeassistant.companion.android.settings.developer.features

internal interface FeaturesSettingsInteraction {
    fun onBooleanFeatureChanged(feature: Feature.BooleanFeature, newValue: Boolean)
    fun onStringFeatureChanged(feature: Feature.StringFeature, newValue: String)
    fun onStringFeatureSelected(feature: Feature.StringFeature?)
}
