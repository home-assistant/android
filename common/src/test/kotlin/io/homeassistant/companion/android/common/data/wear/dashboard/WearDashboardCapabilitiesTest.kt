package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.ScreenShape
import io.homeassistant.companion.android.common.data.wear.dashboard.model.ScreenSizeBucket
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardCapabilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class WearDashboardCapabilitiesTest {
    @ParameterizedTest
    @MethodSource("roundScreenSizeBuckets")
    fun `Given round screen dimensions when deriving capabilities then size bucket matches`(
        widthDp: Int,
        heightDp: Int,
        expectedBucket: ScreenSizeBucket,
    ) {
        val capabilities = WearDashboardCapabilities.fromDeviceParameters(
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            screenShape = ScreenShape.Round,
            sdkInt = 34,
        )

        assertEquals(expectedBucket, capabilities.screenSizeBucket)
        assertEquals(ScreenShape.Round, capabilities.screenShape)
    }

    @ParameterizedTest
    @MethodSource("squareScreenSizeBuckets")
    fun `Given square screen width when deriving capabilities then size bucket uses width`(
        widthDp: Int,
        heightDp: Int,
        expectedBucket: ScreenSizeBucket,
    ) {
        val capabilities = WearDashboardCapabilities.fromDeviceParameters(
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            screenShape = ScreenShape.Square,
            sdkInt = 34,
        )

        assertEquals(expectedBucket, capabilities.screenSizeBucket)
    }

    @Test
    fun `Given defaults when reading capabilities then conservative values are returned`() {
        val capabilities = WearDashboardCapabilities.defaults()

        assertEquals(ScreenShape.Round, capabilities.screenShape)
        assertEquals(ScreenSizeBucket.Medium, capabilities.screenSizeBucket)
        assertEquals(5, capabilities.maxComponentTreeDepth)
        assertEquals(6, capabilities.maxChildrenPerContainer)
    }

    companion object {
        @JvmStatic
        fun roundScreenSizeBuckets(): List<Arguments> = listOf(
            Arguments.of(170, 170, ScreenSizeBucket.Small),
            Arguments.of(180, 220, ScreenSizeBucket.Small),
            Arguments.of(200, 200, ScreenSizeBucket.Medium),
            Arguments.of(224, 224, ScreenSizeBucket.Medium),
            Arguments.of(225, 225, ScreenSizeBucket.Large),
            Arguments.of(240, 240, ScreenSizeBucket.Large),
        )

        @JvmStatic
        fun squareScreenSizeBuckets(): List<Arguments> = listOf(
            Arguments.of(180, 220, ScreenSizeBucket.Small),
            Arguments.of(200, 240, ScreenSizeBucket.Medium),
            Arguments.of(225, 280, ScreenSizeBucket.Large),
        )
    }
}
