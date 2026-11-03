package io.homeassistant.companion.android.util

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.RemoteException
import io.homeassistant.companion.android.common.util.SdkVersion
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextExtensionsTest {

    private val context = mockk<Context>()

    @Test
    fun `Given an API level below O when checking widget pinning then it is unsupported`() {
        SdkVersion.sdkInt = Build.VERSION_CODES.N

        assertFalse(context.isWidgetPinningSupported())
    }

    @Test
    fun `Given a launcher that supports pinning when checking widget pinning then it is supported`() {
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } returns true }) {
            assertTrue(context.isWidgetPinningSupported())
        }
    }

    @Test
    fun `Given a launcher that does not support pinning when checking widget pinning then it is unsupported`() {
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } returns false }) {
            assertFalse(context.isWidgetPinningSupported())
        }
    }

    @Test
    fun `Given pinning support cannot be read when checking widget pinning then it is unsupported`() {
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } throws RemoteException("binder") }) {
            assertFalse(context.isWidgetPinningSupported())
        }
    }

    private inline fun withAppWidgetManager(configure: AppWidgetManager.() -> Unit, block: () -> Unit) {
        SdkVersion.sdkInt = Build.VERSION_CODES.O
        mockkStatic(AppWidgetManager::class) {
            every { AppWidgetManager.getInstance(context) } returns mockk<AppWidgetManager>().apply(configure)
            block()
        }
    }
}
