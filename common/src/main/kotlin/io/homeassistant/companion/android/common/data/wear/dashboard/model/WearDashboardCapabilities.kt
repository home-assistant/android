package io.homeassistant.companion.android.common.data.wear.dashboard.model

/**
 * Physical screen shape of the Wear device.
 */
enum class ScreenShape {
    Round,
    Square,
}

/**
 * Coarse screen size bucket used to apply layout limits on constrained surfaces.
 */
enum class ScreenSizeBucket {
    Small,
    Medium,
    Large,
}

/**
 * Device and platform capabilities that constrain dashboard rendering.
 */
data class WearDashboardCapabilities(
    val screenShape: ScreenShape,
    val screenSizeBucket: ScreenSizeBucket,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val sdkInt: Int,
    val maxComponentTreeDepth: Int,
    val maxChildrenPerContainer: Int,
    val maxDynamicExpressions: Int,
    val supportsDynamicExpressions: Boolean,
    val supportsScrollableLayout: Boolean,
    val supportsOngoingActivity: Boolean,
) {
    companion object {
        private const val SMALL_SCREEN_MAX_DP = 180
        private const val MEDIUM_SCREEN_MAX_DP = 224
        private const val DEFAULT_MAX_COMPONENT_TREE_DEPTH = 5
        private const val DEFAULT_MAX_CHILDREN_PER_CONTAINER = 6
        private const val DEFAULT_MAX_DYNAMIC_EXPRESSIONS = 4
        private const val DYNAMIC_EXPRESSIONS_MIN_SDK = 34
        private const val ONGOING_ACTIVITY_MIN_SDK = 30

        /**
         * Derives capabilities from device display parameters.
         */
        fun fromDeviceParameters(
            screenWidthDp: Int,
            screenHeightDp: Int,
            screenShape: ScreenShape,
            sdkInt: Int,
        ): WearDashboardCapabilities {
            val sizeReferenceDp = when (screenShape) {
                ScreenShape.Round -> minOf(screenWidthDp, screenHeightDp)
                ScreenShape.Square -> screenWidthDp
            }
            return WearDashboardCapabilities(
                screenShape = screenShape,
                screenSizeBucket = sizeBucketFor(sizeReferenceDp),
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                sdkInt = sdkInt,
                maxComponentTreeDepth = DEFAULT_MAX_COMPONENT_TREE_DEPTH,
                maxChildrenPerContainer = DEFAULT_MAX_CHILDREN_PER_CONTAINER,
                maxDynamicExpressions = DEFAULT_MAX_DYNAMIC_EXPRESSIONS,
                supportsDynamicExpressions = sdkInt >= DYNAMIC_EXPRESSIONS_MIN_SDK,
                supportsScrollableLayout = true,
                supportsOngoingActivity = sdkInt >= ONGOING_ACTIVITY_MIN_SDK,
            )
        }

        /**
         * Returns conservative defaults when device parameters are unavailable.
         */
        fun defaults(): WearDashboardCapabilities = WearDashboardCapabilities(
            screenShape = ScreenShape.Round,
            screenSizeBucket = ScreenSizeBucket.Medium,
            screenWidthDp = 200,
            screenHeightDp = 200,
            sdkInt = DYNAMIC_EXPRESSIONS_MIN_SDK,
            maxComponentTreeDepth = DEFAULT_MAX_COMPONENT_TREE_DEPTH,
            maxChildrenPerContainer = DEFAULT_MAX_CHILDREN_PER_CONTAINER,
            maxDynamicExpressions = DEFAULT_MAX_DYNAMIC_EXPRESSIONS,
            supportsDynamicExpressions = true,
            supportsScrollableLayout = true,
            supportsOngoingActivity = true,
        )

        private fun sizeBucketFor(sizeReferenceDp: Int): ScreenSizeBucket = when {
            sizeReferenceDp <= SMALL_SCREEN_MAX_DP -> ScreenSizeBucket.Small
            sizeReferenceDp <= MEDIUM_SCREEN_MAX_DP -> ScreenSizeBucket.Medium
            else -> ScreenSizeBucket.Large
        }
    }
}
