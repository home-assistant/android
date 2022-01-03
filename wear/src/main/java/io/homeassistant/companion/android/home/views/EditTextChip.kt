package io.homeassistant.companion.android.home.views

import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.theme.wearColorPalette

@Composable
fun EditTextChip(
    label: String,
    value: String,
    icon: @Composable() () -> Unit,
    onValueChanged: (String) -> Unit
) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it.data?.let { data ->
                val results: Bundle = RemoteInput.getResultsFromIntent(data)
                val newValue: CharSequence? = results.getCharSequence(label)
                onValueChanged(newValue.toString())
            }
        }

    Chip(
        label = { Text(label) },
        secondaryLabel = { if (value.isNotEmpty()) Text(value) },
        icon = icon,
        onClick = {
            val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            val remoteInputs: List<RemoteInput> = listOf(
                RemoteInput.Builder(label).apply {
                    setLabel(label)
                    wearableExtender {
                        setEmojisAllowed(false)
                        setInputActionType(EditorInfo.IME_ACTION_DONE)
                    }
                    // Set current values as choice, to be able to edit on watch
                    setChoices(arrayOf(value))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setEditChoicesBeforeSending(RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED)
                    }
                }.build()
            )

            RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)

            launcher.launch(intent)
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier
            .fillMaxWidth()
    )
}

@Preview
@Composable
fun PreviewTextField() {
    Column {
        EditTextChip(
            "Template tile content",
            "value",
            {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_text_box,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            }
        ) { }
        EditTextChip(
            "Template tile content",
            "",
            {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_text_box,
                    colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                )
            }
        ) { }
    }
}
