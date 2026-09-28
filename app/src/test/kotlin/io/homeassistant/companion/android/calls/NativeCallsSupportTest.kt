package io.homeassistant.companion.android.calls

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.ApplicationModule
import io.homeassistant.companion.android.common.util.SdkVersion
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34])
class NativeCallsSupportTest {
    @After
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    @Test
    fun `Given supported Android versions when advertising calls then check the platform feature for that version`() {
        for (version in listOf(26, 32, 33, 34)) {
            SdkVersion.sdkInt = version
            val manager = mockk<PackageManager>()
            val context = mockk<Context>()
            every { context.packageManager } returns manager
            every { manager.hasSystemFeature(any()) } returns false
            val feature = if (version >= 33) PackageManager.FEATURE_TELECOM else PackageManager.FEATURE_CONNECTION_SERVICE
            every { manager.hasSystemFeature(feature) } returns true
            every { manager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) } returns true
            assertTrue(ApplicationModule.providesNativeCallsSupport(context))
        }
    }

    @Test
    fun `Given an unsupported device when advertising calls then leave native calling disabled`() {
        val manager = mockk<PackageManager>()
        val context = mockk<Context>()
        every { context.packageManager } returns manager
        every { manager.hasSystemFeature(any()) } returns false
        SdkVersion.sdkInt = 34
        assertFalse(ApplicationModule.providesNativeCallsSupport(context))
        every { manager.hasSystemFeature(PackageManager.FEATURE_TELECOM) } returns true
        assertFalse(ApplicationModule.providesNativeCallsSupport(context))
        every { manager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) } returns true
        assertTrue(ApplicationModule.providesNativeCallsSupport(context))
        every { manager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) } returns true
        assertFalse(ApplicationModule.providesNativeCallsSupport(context))
        SdkVersion.sdkInt = 25
        assertFalse(ApplicationModule.providesNativeCallsSupport(context))
    }
}
