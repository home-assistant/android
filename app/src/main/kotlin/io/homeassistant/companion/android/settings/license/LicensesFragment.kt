package io.homeassistant.companion.android.settings.license

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.Fragment
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.DefaultChipColors
import com.mikepenz.aboutlibraries.ui.compose.DefaultLibraryColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LicenseDialog
import com.mikepenz.aboutlibraries.ui.compose.m3.LicenseDialogBody
import com.mikepenz.aboutlibraries.ui.compose.m3.sheet.LibraryDetailSheet
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantTextStyles
import com.mikepenz.aboutlibraries.ui.compose.style.LibrariesStyle
import com.mikepenz.aboutlibraries.ui.compose.style.LicenseHueResolver
import com.mikepenz.aboutlibraries.ui.compose.style.VariantColors
import com.mikepenz.aboutlibraries.ui.compose.style.librariesStyle
import com.mikepenz.aboutlibraries.ui.compose.variant.Libraries
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryActionMode
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
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
    var openDialog by remember { mutableStateOf<Library?>(null) }
    var openSheet by remember { mutableStateOf<Library?>(null) }

    LicensesContent(
        libraries = libraries,
        dialogLibrary = openDialog,
        sheetLibrary = openSheet,
        onDialogLibraryChange = { openDialog = it },
        onSheetLibraryChange = { openSheet = it },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensesContent(
    libraries: Libs?,
    dialogLibrary: Library?,
    sheetLibrary: Library?,
    onDialogLibraryChange: (Library?) -> Unit,
    onSheetLibraryChange: (Library?) -> Unit,
) {
    val colors = rememberLibraryColors()
    val variantColors = rememberLibraryVariantColors()
    val style = rememberLibraryStyle(variantColors)
    val sheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true)

    // We replicate LibrariesContainer here instead of using it directly because the container
    // composable builds its LibrariesStyle internally and doesn't expose it, so there is no way to
    // override the text styles.
    Libraries(
        libraries = libraries?.libraries.orEmpty(),
        style = style,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.libraryBackgroundColor),
        detailMode = LibraryDetailMode.Sheet,
        actionMode = LibraryActionMode.Chips,
        contentPadding = safeBottomWindowInsets(applyHorizontal = false).asPaddingValues(),
        onSheetRequest = { onSheetLibraryChange(it) },
        onDialogRequest = { onDialogLibraryChange(it) },
    )

    dialogLibrary?.let { library ->
        LicenseDialog(
            library = library,
            style = style,
            colors = colors,
            confirmText = stringResource(commonR.string.ok),
            body = { lib, modifier -> LicenseDialogBody(library = lib, colors = colors, modifier = modifier) },
            onDismiss = { onDialogLibraryChange(null) },
        )
    }

    sheetLibrary?.let { library ->
        LibraryDetailSheet(
            library = library,
            onDismiss = { onSheetLibraryChange(null) },
            style = style,
            sheetState = sheetState,
            actionMode = LibraryActionMode.Chips,
        )
    }
}

@Composable
private fun rememberLibraryColors(): LibraryColors {
    val colorScheme = LocalHAColorScheme.current
    return remember(colorScheme) {
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
}

@Composable
private fun rememberLibraryVariantColors(): VariantColors {
    val colorScheme = LocalHAColorScheme.current
    return LibraryDefaults.m3VariantColors(
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
        licenseHueResolver = LicenseHueResolver { colorScheme.colorFillPrimaryLoudResting },
    )
}

@Composable
private fun rememberLibraryStyle(variantColors: VariantColors): LibrariesStyle = LibraryDefaults.librariesStyle(
    colors = variantColors,
    textStyles = LibraryDefaults.m3VariantTextStyles(
        nameTextStyle = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        authorTextStyle = HATextStyle.Body,
        versionTextStyle = HATextStyle.BodyMedium,
        licenseTextStyle = HATextStyle.BodyMedium,
        descriptionTextStyle = HATextStyle.Body,
        headerTitleTextStyle = HATextStyle.Headline,
        headerTaglineTextStyle = HATextStyle.Body,
        tabTextStyle = HATextStyle.Button,
        tabCountTextStyle = HATextStyle.Body,
        sheetTitleTextStyle = HATextStyle.Headline,
        sheetMetaTextStyle = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        sheetBodyTextStyle = HATextStyle.Body,
        actionLinkTextStyle = HATextStyle.Button,
        actionChipTextStyle = HATextStyle.Button,
    ),
)
