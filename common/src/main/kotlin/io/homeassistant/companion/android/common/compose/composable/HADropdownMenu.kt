package io.homeassistant.companion.android.common.compose.composable

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** Minimum height for the dropdown popup, ensuring at least 2 items are visible on small screens. */
private val MIN_DROPDOWN_HEIGHT = 112.dp

/**
 * Represents a selectable item in an [HADropdownMenu].
 *
 * The generic type [T] allows using any type as the selection key (enums, sealed classes, ints, etc.),
 * enforcing compile-time type safety on the selection. The key type must have proper [equals] and
 * [hashCode] implementations.
 *
 * @param T The type of the selection key
 * @param key Unique identifier for this item, used for selection tracking
 * @param label Display text shown in the dropdown list and in the collapsed field when selected
 */
@Immutable
data class HADropdownItem<T>(val key: T, val label: String)

/**
 * Remembers the currently selected dropdown key as a [MutableState].
 *
 * This is a convenience wrapper that avoids verbose `remember { mutableStateOf<T?>(null) }`
 * at call sites. The generic type [T] is inferred from the [initialKey] argument, or can be
 * specified explicitly (e.g., `rememberSelectedDropdownKey<MyEnum>()`).
 *
 * @param T The type of the selection key
 * @param initialKey The initially selected key, or null if nothing is selected
 * @return A [MutableState] holding the currently selected key
 */
@Composable
fun <T> rememberSelectedDropdownKey(initialKey: T? = null): MutableState<T?> =
    remember(initialKey) { mutableStateOf(initialKey) }

/**
 * A dropdown menu component for selecting a single item from a list.
 *
 * Opens a popup [DropdownMenu] anchored below the field when tapped.
 * The collapsed state displays either the selected item's label or a placeholder text
 * inside an outlined field.
 *
 * The generic type [T] enforces type-safe selection keys, following the same pattern as
 * [HARadioGroup].
 *
 * @param T The type of the selection key
 * @param items The list of available items to choose from
 * @param selectedKey The key of the currently selected item, or null if none selected
 * @param onItemSelected Callback invoked with the selected item's key when an item is tapped
 * @param modifier The modifier to apply to this composable
 * @param label Optional label displayed above the selected value inside the field
 * @param placeholder Optional text shown when no item is selected.
 * @param enabled Controls whether the dropdown can be opened. When false, the field is not clickable
 */
@Composable
fun <T> HADropdownMenu(
    @SuppressLint("ComposeUnstableCollections") items: List<HADropdownItem<T>>,
    selectedKey: T?,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    HADropdownMenuInternal(
        items = items,
        selectedKey = selectedKey,
        onItemSelected = onItemSelected,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
    )
}

@Composable
@VisibleForTesting
internal fun <T> HADropdownMenuInternal(
    @SuppressLint("ComposeUnstableCollections") items: List<HADropdownItem<T>>,
    selectedKey: T?,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    val selectedItem = selectedKey?.let { key -> items.firstOrNull { it.key == key } }
    val density = LocalDensity.current
    var fieldWidth by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier) {
        DropdownField(
            selectedLabel = selectedItem?.label,
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            expanded = isExpanded,
            onClick = { if (enabled) isExpanded = !isExpanded },
            modifier = Modifier.onGloballyPositioned { coordinates ->
                fieldWidth = with(density) { coordinates.size.width.toDp() }
            },
        )

        DropdownPopupMenu(
            items = items,
            selectedKey = selectedKey,
            expanded = isExpanded,
            onItemSelected = { key ->
                onItemSelected(key)
                isExpanded = false
            },
            onDismissRequest = { isExpanded = false },
            modifier = Modifier.width(fieldWidth),
        )
    }
}

/**
 * The collapsed field that displays the selected value or placeholder.
 * Renders as a settings card container that opens the selection list on tap.
 */
@Composable
private fun DropdownField(
    selectedLabel: String?,
    label: String?,
    placeholder: String?,
    enabled: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "dropdownArrowRotation",
    )
    val cornerShape = RoundedCornerShape(HARadius.XL)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.colorSurfaceLow, shape = cornerShape)
            .clip(cornerShape)
            .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
            .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (label != null) {
                Text(
                    text = label,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                    color = if (enabled) colorScheme.colorTextSecondary else colorScheme.colorTextDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = selectedLabel ?: placeholder ?: "",
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                color = when {
                    !enabled -> colorScheme.colorTextDisabled
                    selectedLabel != null -> colorScheme.colorTextPrimary
                    else -> colorScheme.colorTextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = if (enabled) colorScheme.colorTextSecondary else colorScheme.colorTextDisabled,
            modifier = Modifier
                .size(HADimens.SPACE5)
                .rotate(arrowRotation),
        )
    }
}

/**
 * A popup menu anchored for the dropdown field.
 * Uses Material 3 [DropdownMenu] with [DropdownMenuItem] for each item.
 */
@Composable
private fun <T> DropdownPopupMenu(
    @SuppressLint("ComposeUnstableCollections") items: List<HADropdownItem<T>>,
    selectedKey: T?,
    expanded: Boolean,
    onItemSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    val density = LocalDensity.current
    val containerHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    // Prefer to keep the dropdown height around 1/3 of the screen on larger displays, while also enforcing a
    // minimum height so at least 2 items are visible on small screens. On very small screens this minimum may
    // cause the dropdown to exceed 1/3 of the screen height, but it avoids the menu being unusably short.
    val maxHeight = maxOf(containerHeight / 3, MIN_DROPDOWN_HEIGHT)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = colorScheme.colorSurfaceDefault,
        modifier = modifier.heightIn(max = maxHeight),
        shape = RoundedCornerShape(HARadius.XL),
    ) {
        items.forEach { item ->
            val isSelected = item.key == selectedKey
            DropdownMenuItem(
                text = {
                    Text(
                        text = item.label,
                        style = HATextStyle.Body,
                        color = colorScheme.colorTextPrimary,
                    )
                },
                onClick = { onItemSelected(item.key) },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colorScheme.colorFillPrimaryLoudResting,
                            modifier = Modifier.size(HADimens.SPACE5),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

private enum class SampleServer {
    HOME,
    OFFICE,
    VACATION_HOUSE,
}

private val sampleItems = listOf(
    HADropdownItem(key = SampleServer.HOME, label = "Home"),
    HADropdownItem(key = SampleServer.OFFICE, label = "Office"),
    HADropdownItem(key = SampleServer.VACATION_HOUSE, label = "Vacation House"),
)

@Preview
@Composable
private fun HADropdownMenuCollapsedPreview() {
    HAThemeForPreview {
        HADropdownMenu(
            items = sampleItems,
            selectedKey = null,
            onItemSelected = {},
            label = "Server",
        )
    }
}

@Preview
@Composable
private fun HADropdownMenuSelectedPreview() {
    HAThemeForPreview {
        HADropdownMenu(
            items = sampleItems,
            selectedKey = SampleServer.HOME,
            onItemSelected = {},
            label = "Server",
        )
    }
}

@Preview
@Composable
private fun HADropdownMenuDisabledPreview() {
    HAThemeForPreview {
        HADropdownMenu(
            items = sampleItems,
            selectedKey = SampleServer.HOME,
            onItemSelected = {},
            label = "Server",
            enabled = false,
        )
    }
}

@Preview
@Composable
private fun HADropdownMenuNoLabelPreview() {
    HAThemeForPreview {
        HADropdownMenuInternal(
            items = sampleItems,
            selectedKey = null,
            onItemSelected = {},
            initiallyExpanded = true,
        )
    }
}
