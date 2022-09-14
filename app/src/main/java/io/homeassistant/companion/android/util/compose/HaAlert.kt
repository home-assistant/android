package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun HaAlertWarning(
    message: String,
    action: String?,
    onActionClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(commonR.color.colorAlertWarning), MaterialTheme.shapes.medium)
            .padding(all = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            modifier = Modifier
                .weight(1f)
                .padding(end = if (action != null) 8.dp else 0.dp)
        )
        if (action != null) {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = colorResource(commonR.color.colorOnAlertWarning)),
                onClick = onActionClicked
            ) {
                Text(action)
            }
        }
    }
}
