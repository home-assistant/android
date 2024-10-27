package io.homeassistant.companion.android.common.util.feature

// Dummy feature definitions for testing
internal object TestBooleanFeature : FeatureDefinition.BooleanFeatureDefinition {
    override val featureName: String = "TestBooleanFeature"
    override val defaultValue: Boolean = false
}

internal object TestStringFeature : FeatureDefinition.StringFeatureDefinition {
    override val featureName: String = "TestStringFeature"
    override val defaultValue: String = "default"
}
