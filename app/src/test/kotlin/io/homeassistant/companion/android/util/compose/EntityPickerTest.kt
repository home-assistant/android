package io.homeassistant.companion.android.util.compose

import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class EntityPickerTest {

    @ParameterizedTest(name = "Domain \"{0}\" icon \"{1}\" should resolve to a valid icon")
    @MethodSource("fallbackDomainIconsProvider")
    fun `Given a domain fallback icon when resolving it then it returns a valid icon`(
        domain: String,
        mdiIconName: String,
    ) {
        val icon = CommunityMaterial.getIconByMdiName(mdiIconName)

        assertNotNull(icon) { "Icon '$mdiIconName' for domain '$domain' could not be resolved." }
    }

    companion object {
        @JvmStatic
        fun fallbackDomainIconsProvider(): List<Arguments> {
            return FALLBACK_DOMAIN_ICONS.map { (domain, icon) ->
                Arguments.of(domain, icon)
            }
        }
    }
}
