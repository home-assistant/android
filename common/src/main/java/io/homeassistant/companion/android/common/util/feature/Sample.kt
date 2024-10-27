package io.homeassistant.companion.android.common.util.feature

/**
 * Samples of features used to demonstrate how to create and use them.
 * In practice the definition of a feature should happen near it's usage, to ease it's removal later.
 */

data object BooleanSampleFeature : FeatureDefinition.BooleanFeatureDefinition {
    override val featureName: String = "Sample feature"
    override val defaultValue: Boolean = false
}

data object StringSampleFeature : FeatureDefinition.StringFeatureDefinition {
    override val featureName: String = "Sample editable string"
    override val defaultValue: String = "default value"
}

data object ImmutableStringSampleFeature : FeatureDefinition.StringFeatureDefinition {
    override val featureName: String = "Sample immutable string"
    override val defaultValue: String = "default value"
}
