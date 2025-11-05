package io.homeassistant.companion.android.developer.catalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HAProgress
import io.homeassistant.companion.android.common.compose.theme.HASize

internal const val BIG_CONTENT =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

fun LazyListScope.catalogButtonsAndIndicatorsSection(variant: ButtonVariant) {
    buttonSection(variant = variant, enabled = true)
    buttonSection(variant = variant, enabled = false)
    buttonsWithIcon(variant = variant)
    buttonsWithBigContent(variant = variant)
    progress()
}

private fun LazyListScope.buttonSection(variant: ButtonVariant, enabled: Boolean) {
    catalogSection(title = "Buttons ${if (enabled) "enabled" else "disabled"}") {
        CatalogRow {
            ButtonSize.entries.forEach {
                HAAccentButton(
                    text = "Label",
                    enabled = enabled,
                    onClick = {},
                    variant = variant,
                    size = it,
                )
            }
            ButtonSize.entries.forEach {
                HAFilledButton(
                    text = "Label",
                    enabled = enabled,
                    onClick = {},
                    variant = variant,
                    size = it,
                )
            }
            ButtonSize.entries.forEach {
                HAPlainButton(
                    text = "Label",
                    enabled = enabled,
                    onClick = {},
                    variant = variant,
                    size = it,
                )
            }
            HAIconButton(
                Icons.Default.Build,
                onClick = {},
                contentDescription = null,
                variant = variant,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun AddIcon() {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun LazyListScope.buttonsWithIcon(variant: ButtonVariant) {
    catalogSection(title = "Buttons with Icon") {
        CatalogRow {
            HAAccentButton(
                text = "Label",
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
            )
            HAFilledButton(
                text = "Label",
                onClick = {},
                variant = variant,
                suffix = { AddIcon() },
            )
            HAPlainButton(
                text = "Label",
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAAccentButton(
                text = "Label",
                onClick = {},
                enabled = false,
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
        }
    }
}

private fun LazyListScope.buttonsWithBigContent(variant: ButtonVariant) {
    catalogSection(title = "Button with big content") {
        CatalogRow {
            HAAccentButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAAccentButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
                maxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
            HAFilledButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAFilledButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },

                maxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
            HAPlainButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAPlainButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
                maxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyListScope.progress() {
    catalogSection(title = "Progress") {
        CatalogRow {
            HALoading(modifier = Modifier.size(HASize.X5L))
            var progress by remember { mutableFloatStateOf(0.1f) }
            val animatedProgress by
                animateFloatAsState(
                    targetValue = progress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "Progress",
                )
            HAProgress(
                { animatedProgress },
                modifier = Modifier.clickable(
                    onClick = {
                        progress = 1f
                    },
                ),
            )
        }
    }
}

@Preview
@Composable
fun PreviewHAButtonsAndIndicatorsScreen() {
    LazyColumn {
        catalogButtonsAndIndicatorsSection(ButtonVariant.PRIMARY)
    }
}
