package io.homeassistant.companion.android.settings.wear.views

import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.text.HtmlCompat.fromHtml
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.intervalToString
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun SettingsWearTemplateTile(
    template: String,
    renderedTemplate: String,
    refreshInterval: Int,
    onContentChanged: (String) -> Unit,
    onRefreshIntervalChanged: (Int) -> Unit,
    onBackClicked: () -> Unit,
) {
    Scaffold(
        topBar = {
            SettingsWearTopAppBar(
                title = { Text(stringResource(commonR.string.template_tile)) },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(safeBottomPaddingValues())
                .padding(padding)
                .padding(all = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_timer_cog,
                    colorFilter = ColorFilter.tint(colorResource(commonR.color.colorPrimary)),
                    modifier = Modifier
                        .height(24.dp)
                        .width(24.dp),
                )
                Text(
                    stringResource(commonR.string.refresh_interval),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
                Box {
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                    ) {
                        Text(intervalToString(LocalContext.current, refreshInterval))
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        val options = listOf(
                            0, 1, 60, 2 * 60, 5 * 60, 10 * 60, 15 * 60, 30 * 60, 60 * 60, 2 * 60 * 60,
                            5 * 60 * 60, 10 * 60 * 60, 24 * 60 * 60,
                        )
                        for (option in options) {
                            DropdownMenuItem(onClick = {
                                onRefreshIntervalChanged(option)
                                dropdownExpanded = false
                            }) {
                                Text(intervalToString(LocalContext.current, option))
                            }
                        }
                    }
                }
            }
            Text(stringResource(commonR.string.template_tile_help))
            TextField(
                value = template,
                onValueChange = onContentChanged,
                label = {
                    Text(stringResource(commonR.string.template_tile_content))
                },
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 10,
            )
            Text(
                parseHtml(renderedTemplate),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun parseHtml(renderedText: String) = buildAnnotatedString {
    // Replace control char \r\n, \r, \n and also \r\n, \r, \n as text literals in strings to <br>
    val renderedSpanned =
        fromHtml(renderedText.replace("(\r\n|\r|\n)|(\\\\r\\\\n|\\\\r|\\\\n)".toRegex(), "<br>"), FROM_HTML_MODE_LEGACY)
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
private fun PreviewSettingsWearTemplateTile() {
    SettingsWearTemplateTile(
        template = "Example entity: {{ states('sensor.example_entity') }}",
        renderedTemplate = "Example entity: Lorem ipsum",
        refreshInterval = 300,
        onContentChanged = {},
        onRefreshIntervalChanged = {},
        onBackClicked = {},
    )
}
