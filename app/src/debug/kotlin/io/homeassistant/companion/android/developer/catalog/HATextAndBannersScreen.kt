package io.homeassistant.companion.android.developer.catalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HADetails
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAProgress
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

fun LazyListScope.catalogTextAndBannersSection() {
    textStyles()
    banners()
    details()
    progress()
}

private fun LazyListScope.textStyles() {
    catalogSection(title = "Text Style") {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Headline",
                style = HATextStyle.Headline,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Body",
                style = HATextStyle.Body,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "BodyMedium",
                style = HATextStyle.BodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "UserInput",
                style = HATextStyle.UserInput,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Button",
                style = HATextStyle.Button,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun LazyListScope.banners() {
    catalogSection(title = "Banners") {
        CatalogRow {
            HAHint("This is a small hint banner")
            HAHint("This is a hint banner with a close button") {}
            HABanner {
                Text("Simple customizable banner", color = Color.Red)
            }
        }
    }
}

private fun LazyListScope.details() {
    catalogSection(title = "Details") {
        CatalogRow {
            HADetails("Hello") {
                Text("Content", style = HATextStyle.Body)
            }
            HADetails("Hello", defaultExpanded = true) {
                Text("Content", style = HATextStyle.Body)
            }
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

@Preview(showBackground = true, device = TABLET)
@Composable
private fun PreviewHATextAndBannersScreen() {
    HAThemeForPreview {
        LazyColumn {
            catalogTextAndBannersSection()
        }
    }
}
