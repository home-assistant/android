package io.homeassistant.companion.android.settings.backup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** Uses the icon gutter and compact accent headings of the Companion settings list. */
@Composable
internal fun BackupSettingsSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Text(
            stringResource(title),
            style = HATextStyle.BodyMedium,
            fontWeight = FontWeight.Medium,
            color = LocalHAColorScheme.current.colorTextLink,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(start = HADimens.SPACE18, end = HADimens.SPACE4).semantics { heading() },
        )
        BackupInsetContent(content)
    }
}

@Composable
internal fun BackupInsetContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        content = content,
    )
}
