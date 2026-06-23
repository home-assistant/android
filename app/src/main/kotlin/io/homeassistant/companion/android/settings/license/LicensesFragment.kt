package io.homeassistant.companion.android.settings.license

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.DefaultChipColors
import com.mikepenz.aboutlibraries.ui.compose.DefaultLibraryColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.safeBottomWindowInsets

class LicensesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HATheme {
                    LicensesContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.licenses)
    }
}

@Composable
private fun LicensesContent() {
    val libraries by produceLibraries()
    LicensesContent(libraries)
}

@Composable
internal fun LicensesContent(libraries: Libs?) {
    val colorScheme = LocalHAColorScheme.current
    Scaffold(
        contentWindowInsets = safeBottomWindowInsets(applyHorizontal = false),
    ) { contentPadding ->
        val colors = remember {
            DefaultLibraryColors(
                libraryBackgroundColor = colorScheme.colorSurfaceDefault,
                libraryContentColor = colorScheme.colorTextPrimary,
                versionChipColors = DefaultChipColors(
                    containerColor = colorScheme.colorFillNeutralNormalResting,
                    contentColor = colorScheme.colorOnNeutralNormal,
                ),
                licenseChipColors = DefaultChipColors(
                    containerColor = colorScheme.colorFillPrimaryNormalResting,
                    contentColor = colorScheme.colorOnPrimaryNormal,
                ),
                fundingChipColors = DefaultChipColors(
                    containerColor = colorScheme.colorFillPrimaryNormalResting,
                    contentColor = colorScheme.colorOnPrimaryNormal,
                ),
                dialogBackgroundColor = colorScheme.colorSurfaceDefault,
                dialogContentColor = colorScheme.colorTextPrimary,
                dialogConfirmButtonColor = colorScheme.colorOnPrimaryNormal,
            )
        }
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            // Override default value to provide translatable string
            licenseDialogConfirmText = stringResource(commonR.string.ok),
            colors = colors,
            // Override the variant color tokens with the Home Assistant color scheme.
            // licenseHueResolver and contrastLevel keep their M3 defaults: license dot/badge
            // hues are derived from the accent (primary) at normal contrast.
            variantColors = LibraryDefaults.m3VariantColors(
                headerBackground = colorScheme.colorSurfaceLow,
                headerOnBackground = colorScheme.colorTextPrimary,
                headerSubtleContent = colorScheme.colorTextSecondary,
                headerDivider = colorScheme.colorBorderNeutralQuiet,
                rowBackground = colorScheme.colorSurfaceDefault,
                rowExpandedBackground = colorScheme.colorSurfaceLow,
                rowOnBackground = colorScheme.colorTextPrimary,
                rowSubtleContent = colorScheme.colorTextSecondary,
                rowDivider = colorScheme.colorBorderNeutralQuiet,
                actionFilledContainer = colorScheme.colorFillPrimaryLoudResting,
                actionFilledContent = colorScheme.colorOnPrimaryLoud,
                actionOutlineBorder = colorScheme.colorBorderNeutralNormal,
                actionOutlineContent = colorScheme.colorTextPrimary,
                actionLinkColor = colorScheme.colorTextLink,
                tabIdleBackground = colorScheme.colorFillNeutralNormalResting,
                tabIdleContent = colorScheme.colorTextSecondary,
                tabActiveBackground = colorScheme.colorFillPrimaryNormalResting,
                tabActiveBorder = colorScheme.colorBorderPrimaryNormal,
                tabActiveContent = colorScheme.colorOnPrimaryNormal,
                sheetScrim = colorScheme.colorOverlayModal,
                sheetSurface = colorScheme.colorSurfaceDefault,
                sheetSurfaceVariant = colorScheme.colorSurfaceLow,
                sheetDragHandle = colorScheme.colorBorderNeutralNormal,
            ),
        )
    }
}
