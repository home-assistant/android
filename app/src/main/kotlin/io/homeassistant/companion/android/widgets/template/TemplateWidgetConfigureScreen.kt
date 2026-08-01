package io.homeassistant.companion.android.widgets.template

import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.widgets.WidgetBackgroundTypeDropdown
import io.homeassistant.companion.android.widgets.WidgetTextColor
import io.homeassistant.companion.android.widgets.WidgetTextColorDropdown

/**
 * Configuration screen of the template widget, bound to its [TemplateWidgetConfigureViewModel].
 *
 * @param canNavigateBack Whether leaving goes back to a previous screen, offering a back arrow
 * instead of a close button.
 */
@Composable
internal fun TemplateWidgetConfigureScreen(
    viewModel: TemplateWidgetConfigureViewModel,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect { resId ->
            snackbarHostState.showSnackbar(resources.getString(resId))
        }
    }

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
    Scaffold(
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.create_template)) },
                onBackClick = onNavigate.takeIf { canNavigateBack },
                onCloseClick = onNavigate.takeIf { !canNavigateBack },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(HADimens.SPACE4)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            ServerSelector(
                items = state.serversDropdownItems,
                selectedServerId = state.selectedServerId,
                showServerSelector = state.showServerSelector,
                onServerSelected = onServerSelected,
            )
            TemplateSection(
                template = state.template,
                preview = state.preview,
                onTemplateChanged = onTemplateChanged,
            )
            HATextField(
                value = state.textSize,
                onValueChange = onTextSizeChanged,
                label = { Text(stringResource(commonR.string.widget_text_size_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                maxLines = 1,
                modifier = Modifier.formControlWidth(),
            )
            AppearanceSection(
                selectedBackgroundType = state.selectedBackgroundType,
                dynamicColorAvailable = state.dynamicColorAvailable,
                textColorHex = state.textColorHex,
                onBackgroundTypeSelected = onBackgroundTypeSelected,
                onTextColorSelected = onTextColorSelected,
            )
            HAAccentButton(
                text = stringResource(state.actionButtonLabel),
                onClick = onActionClick,
                modifier = Modifier.formControlWidth(),
                enabled = state.isActionEnabled,
            )
        }
    }
}

@Composable
private fun ServerSelector(
    items: List<HADropdownItem<Int>>,
    selectedServerId: Int,
    showServerSelector: Boolean,
    onServerSelected: (Int) -> Unit,
) {
    if (!showServerSelector) return

    HADropdownMenu(
        items = items,
        selectedKey = selectedServerId,
        onItemSelected = onServerSelected,
        label = stringResource(commonR.string.server_select),
        placeholder = stringResource(commonR.string.server_select),
        modifier = Modifier.formControlWidth(),
        enabled = items.isNotEmpty(),
    )
}

@Composable
private fun TemplateSection(template: String, preview: TemplatePreview, onTemplateChanged: (String) -> Unit) {
    HATextField(
        value = template,
        onValueChange = onTemplateChanged,
        label = { Text(stringResource(commonR.string.template)) },
        placeholder = { Text(stringResource(commonR.string.template_widget_default)) },
        modifier = Modifier.formControlWidth(),
    )

    Text(text = preview.toAnnotatedString(), modifier = Modifier.formControlWidth())
}

@Composable
private fun TemplatePreview.toAnnotatedString(): AnnotatedString = when (this) {
    is TemplatePreview.Empty -> AnnotatedString(stringResource(commonR.string.empty_template))
    is TemplatePreview.Error -> AnnotatedString(stringResource(messageRes))
    is TemplatePreview.Rendered -> parseHtml(text)
}

@Composable
private fun AppearanceSection(
    selectedBackgroundType: WidgetBackgroundType,
    dynamicColorAvailable: Boolean,
    textColorHex: String?,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
) {
    WidgetBackgroundTypeDropdown(
        selected = selectedBackgroundType,
        dynamicColorAvailable = dynamicColorAvailable,
        onSelected = onBackgroundTypeSelected,
        modifier = Modifier.formControlWidth(),
    )

    if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
        // Widgets persist the resolved hex, so the Context needed to convert stays in the UI layer.
        val context = LocalContext.current
        val selected = remember(context, textColorHex) { WidgetTextColor.fromHex(context, textColorHex) }

        WidgetTextColorDropdown(
            selected = selected,
            onSelected = { onTextColorSelected(it.resolve(context)) },
            modifier = Modifier.formControlWidth(),
        )
    }
}

private fun Modifier.formControlWidth(): Modifier = this
    .widthIn(max = MaxButtonWidth)
    .fillMaxWidth()

/**
 * Converts a rendered template's HTML into an [AnnotatedString], the same approach used to render
 * templates on the Wear OS template tile ([io.homeassistant.companion.android.tiles.TemplateTile]).
 */
private fun parseHtml(renderedText: String): AnnotatedString = buildAnnotatedString {
    // Replace both actual and literal (escaped) line break characters with <br>
    val renderedSpanned = HtmlCompat.fromHtml(
        renderedText.replace("(\r\n|\r|\n)|(\\\\r\\\\n|\\\\r|\\\\n)".toRegex(), "<br>"),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )
    append(renderedSpanned.toString())
    renderedSpanned.getSpans(0, renderedSpanned.length, CharacterStyle::class.java).forEach { span ->
        val start = renderedSpanned.getSpanStart(span)
        val end = renderedSpanned.getSpanEnd(span)
        when (span) {
            is AbsoluteSizeSpan -> addStyle(SpanStyle(fontSize = span.size.sp), start, end)
            is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
            is RelativeSizeSpan -> {
                val defaultSize = 12
                addStyle(SpanStyle(fontSize = (span.sizeChange * defaultSize).sp), start, end)
            }
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
    }
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
