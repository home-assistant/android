package io.homeassistant.companion.android.widgets.template

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Eye
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.parseHtml
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.widgets.configure.WidgetAppearanceSection
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureScaffold
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureScreen
import io.homeassistant.companion.android.widgets.configure.formControlWidth

private const val TEMPLATE_FIELD_MIN_LINES = 3

/**
 * Configuration screen of the template widget, bound to its [TemplateWidgetConfigureViewModel].
 *
 * @param canNavigateBack Whether leaving goes back to a previous screen, offering a back arrow
 * instead of a close button.
 */
@SuppressLint("ComposeViewModelForwarding")
@Composable
internal fun TemplateWidgetConfigureScreen(
    viewModel: TemplateWidgetConfigureViewModel,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
) {
    WidgetConfigureScreen(viewModel) { state, snackbarHostState ->
        TemplateWidgetConfigureContent(
            state = state,
            snackbarHostState = snackbarHostState,
            canNavigateBack = canNavigateBack,
            onNavigate = onNavigate,
            onServerSelected = viewModel::onServerSelected,
            onTemplateChanged = viewModel::onTemplateChanged,
            onTextSizeChanged = viewModel::onTextSizeChanged,
            onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
            onTextColorSelected = viewModel::onTextColorSelected,
            onActionClick = onActionClick,
        )
    }
}

/** Stateless configuration screen for the template widget. */
@Composable
internal fun TemplateWidgetConfigureContent(
    state: TemplateWidgetConfigureState,
    snackbarHostState: SnackbarHostState,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onServerSelected: (Int) -> Unit,
    onTemplateChanged: (String) -> Unit,
    onTextSizeChanged: (String) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
    onActionClick: () -> Unit,
) {
    WidgetConfigureScaffold(
        title = commonR.string.create_template,
        state = state,
        snackbarHostState = snackbarHostState,
        canNavigateBack = canNavigateBack,
        onNavigate = onNavigate,
        onServerSelected = onServerSelected,
        onActionClick = onActionClick,
    ) {
        TemplateSection(
            template = state.template,
            preview = state.preview,
            onTemplateChanged = onTemplateChanged,
        )
        AppearanceSection(
            textSize = state.textSize,
            textSizeError = state.textSizeError,
            selectedBackgroundType = state.selectedBackgroundType,
            dynamicColorAvailable = state.dynamicColorAvailable,
            textColorHex = state.textColorHex,
            onTextSizeChanged = onTextSizeChanged,
            onBackgroundTypeSelected = onBackgroundTypeSelected,
            onTextColorSelected = onTextColorSelected,
        )
    }
}

@Composable
private fun ColumnScope.TemplateSection(
    template: String,
    preview: TemplatePreview,
    onTemplateChanged: (String) -> Unit,
) {
    HATextField(
        value = template,
        onValueChange = onTemplateChanged,
        label = { Text(stringResource(commonR.string.template)) },
        placeholder = { Text(stringResource(commonR.string.template_widget_default)) },
        minLines = TEMPLATE_FIELD_MIN_LINES,
        modifier = Modifier.formControlWidth(),
    )

    TemplatePreviewCard(preview = preview)
}

/** Shows [preview] labelled as such, so it doesn't get mistaken for another input field. */
@Composable
private fun TemplatePreviewCard(preview: TemplatePreview) {
    val colorScheme = LocalHAColorScheme.current

    HASettingsCard(modifier = Modifier.formControlWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                Icon(
                    imageVector = Mdi.Eye.rememberImageVector(),
                    contentDescription = null,
                    tint = colorScheme.colorTextSecondary,
                    modifier = Modifier.size(HASize.M),
                )
                Text(
                    text = stringResource(commonR.string.template_preview_label),
                    style = HATextStyle.BodyMedium,
                    color = colorScheme.colorTextSecondary,
                )
            }
            Text(
                text = preview.toAnnotatedString(),
                style = HATextStyle.BodyMedium,
                color = colorScheme.colorTextPrimary,
            )
        }
    }
}

@Composable
private fun TemplatePreview.toAnnotatedString(): AnnotatedString = when (this) {
    is TemplatePreview.Empty -> AnnotatedString(stringResource(commonR.string.empty_template))
    is TemplatePreview.Error -> AnnotatedString(stringResource(messageRes))
    // Only the parsing itself is remembered: stringResource() above can't be called from within
    // remember's calculation lambda since it disallows composable calls.
    is TemplatePreview.Rendered -> remember(text) { parseHtml(text) }
}

/** Text size field (template-specific) followed by the shared background and text-color selectors. */
@Composable
private fun ColumnScope.AppearanceSection(
    textSize: String,
    @StringRes textSizeError: Int?,
    selectedBackgroundType: WidgetBackgroundType,
    dynamicColorAvailable: Boolean,
    textColorHex: String?,
    onTextSizeChanged: (String) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
) {
    HATextField(
        value = textSize,
        onValueChange = onTextSizeChanged,
        isError = textSizeError != null,
        label = { Text(stringResource(commonR.string.widget_text_size_label)) },
        supportingText = textSizeError?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        maxLines = 1,
        modifier = Modifier.formControlWidth(),
    )
    WidgetAppearanceSection(
        selectedBackgroundType = selectedBackgroundType,
        dynamicColorAvailable = dynamicColorAvailable,
        textColorHex = textColorHex,
        onBackgroundTypeSelected = onBackgroundTypeSelected,
        onTextColorSelected = onTextColorSelected,
    )
}

@Preview
@Composable
private fun TemplateWidgetConfigureContentPreview() {
    HAThemeForPreview {
        TemplateWidgetConfigureContent(
            state = previewTemplateWidgetConfigureState,
            snackbarHostState = remember { SnackbarHostState() },
            canNavigateBack = false,
            onNavigate = {},
            onServerSelected = {},
            onTemplateChanged = {},
            onTextSizeChanged = {},
            onBackgroundTypeSelected = {},
            onTextColorSelected = {},
            onActionClick = {},
        )
    }
}

private val previewTemplateWidgetConfigureState = TemplateWidgetConfigureState(
    selectedServerId = previewServer1.id,
    serversDropdownItems = listOf(previewServer1, previewServer2).map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
    template = "{{ states('sensor.example') }}",
    preview = TemplatePreview.Rendered("42"),
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
)
