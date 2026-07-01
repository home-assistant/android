package io.homeassistant.companion.android.frontend.navigation

import io.homeassistant.companion.android.frontend.navigation.FrontendTarget.Companion.toRawPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FrontendTargetTest {

    private val samples = listOf(
        FrontendTarget.Default,
        FrontendTarget.Path("/lovelace/0"),
        FrontendTarget.Path("/?more-info-entity-id=light.kitchen&foo=a b"),
        FrontendTarget.EntityMoreInfo("light.kitchen"),
    )

    @Test
    fun `Given legacy paths when fromRawPath then maps to the matching target`() {
        assertEquals(FrontendTarget.Default, FrontendTarget.fromRawPath(null))
        assertEquals(FrontendTarget.Path("/lovelace/0"), FrontendTarget.fromRawPath("/lovelace/0"))
        assertEquals(
            FrontendTarget.EntityMoreInfo("light.kitchen"),
            FrontendTarget.fromRawPath("entityId:light.kitchen"),
        )
    }

    @Test
    fun `Given any target when toLegacyPath then round-trips through fromRawPath`() {
        samples.forEach { target ->
            assertEquals(target, FrontendTarget.fromRawPath(target.toRawPath()))
        }
    }

    @Test
    fun `Given default target when toLegacyPath then returns null`() {
        assertNull(FrontendTarget.Default.toRawPath())
    }
}
