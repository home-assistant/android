package io.homeassistant.companion.android.util.compose

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.R

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun InfoNotification(infoString: Int, channelId: String, buttonString: Int) {
    val context = LocalContext.current
    Icon(
        Icons.Outlined.Info,
        contentDescription = stringResource(id = R.string.info),
        modifier = Modifier.padding(top = 40.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 20.dp),
    ) {
        Text(
            text = stringResource(id = infoString),
            fontSize = 15.sp,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            context.startActivity(intent)
        }) {
            Text(stringResource(buttonString))
        }
    }
}
