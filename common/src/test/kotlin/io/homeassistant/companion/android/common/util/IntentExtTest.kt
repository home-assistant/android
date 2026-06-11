package io.homeassistant.companion.android.common.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val OTHER_PACKAGE = "com.example.other"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class IntentExtTest {

    private lateinit var context: Context
    private lateinit var selfPackage: String
    private var failFastThrowable: Throwable? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        selfPackage = context.packageName
        // Capture FailFast instead of crashing the (debug) test JVM when a redirection is detected.
        FailFast.setHandler { throwable, _ -> failFastThrowable = throwable }
    }

    private fun registerActivity(packageName: String, className: String, exported: Boolean) {
        val info = ActivityInfo().apply {
            this.packageName = packageName
            this.name = className
            this.exported = exported
        }
        shadowOf(context.packageManager).addOrUpdateActivity(info)
    }

    @Test
    fun `Given intent targeting our own non-exported component when stripped then explicit targeting is removed`() {
        val className = "$selfPackage.SecretActivity"
        registerActivity(selfPackage, className, exported = false)
        val intent = Intent().setComponent(ComponentName(selfPackage, className))

        intent.stripSelfNonExportedTarget(context)

        assertNull(intent.component)
        assertNull(intent.selector)
        assertNotNull(failFastThrowable, "redirection should be reported through FailFast")
    }

    @Test
    fun `Given intent targeting our own exported component when stripped then component is kept`() {
        val className = "$selfPackage.PublicActivity"
        registerActivity(selfPackage, className, exported = true)
        val component = ComponentName(selfPackage, className)
        val intent = Intent().setComponent(component)

        intent.stripSelfNonExportedTarget(context)

        assertEquals(component, intent.component)
        assertNull(failFastThrowable, "no redirection so FailFast must not fire")
    }

    @Test
    fun `Given intent targeting another app non-exported component when stripped then intent is left untouched`() {
        // Android already blocks launching another app's non-exported component, so we must not
        // interfere with cross-app intents here.
        val className = "$OTHER_PACKAGE.OtherActivity"
        registerActivity(OTHER_PACKAGE, className, exported = false)
        val component = ComponentName(OTHER_PACKAGE, className)
        val intent = Intent().setComponent(component)

        intent.stripSelfNonExportedTarget(context)

        assertEquals(component, intent.component)
        assertNull(failFastThrowable, "no redirection so FailFast must not fire")
    }

    @Test
    fun `Given intent with an unresolvable component when stripped then intent is left untouched`() {
        val component = ComponentName(selfPackage, "$selfPackage.DoesNotExist")
        val intent = Intent().setComponent(component)

        intent.stripSelfNonExportedTarget(context)

        assertEquals(component, intent.component)
        assertNull(failFastThrowable, "no redirection so FailFast must not fire")
    }

    @Test
    fun `Given an intent URI targeting our own non-exported component when parsed then redirection is neutralized`() {
        val className = "$selfPackage.SecretActivity"
        registerActivity(selfPackage, className, exported = false)
        val uri = Intent()
            .setComponent(ComponentName(selfPackage, className))
            .putExtra("attacker", "value")
            .toUri(Intent.URI_INTENT_SCHEME)

        val result = context.parseExternalIntentUri(uri)

        assertNull(result.component)
        assertNull(result.selector)
        assertNotNull(failFastThrowable, "redirection should be reported through FailFast")
    }
}
