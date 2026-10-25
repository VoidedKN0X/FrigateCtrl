@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vknxworks.frigatectrl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.ui.components.CtrlBottomSheet
import com.vknxworks.frigatectrl.ui.components.CtrlSectionLabel
import com.vknxworks.frigatectrl.ui.components.CtrlSwitch
import com.vknxworks.frigatectrl.ui.components.topDivider
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType

// The grouped settings rows every Ctrl app uses (as in MemoCtrl, ProxCtrl and TrueCtrl).

/**
 * A block of related rows: a mono label above ("PRIVACY"), the rows in a surface card with a 1 dp border
 * and 1 dp dividers between them, and an optional note below.
 */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    gutter: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 10.dp)) {
        if (title != null) {
            CtrlSectionLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CtrlShape.Card)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.Outline, CtrlShape.Card),
        ) {
            val counter = remember { GroupRowCounter() }
            CompositionLocalProvider(LocalGroupRowIndex provides counter) { content() }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

/** Gives every row in a [Group] after the first a divider on top. */
private class GroupRowCounter { var count = 0 }
private val LocalGroupRowIndex = staticCompositionLocalOf<GroupRowCounter?> { null }

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = CtrlColors.Text,
    titleColor: Color = CtrlColors.Text,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    val counter = LocalGroupRowIndex.current
    // Counted once per row when it first appears: rows that come and go keep a sensible divider.
    val first = remember(counter) { counter == null || counter.count++ == 0 }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .then(if (!first) Modifier.topDivider(CtrlColors.Outline) else Modifier)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = iconTint.copy(alpha = alpha))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = titleColor.copy(alpha = alpha), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted.copy(alpha = alpha),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (showChevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
        }
    }
}

@Composable
fun GroupSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    GroupRow(title, subtitle, icon, enabled = enabled, onClick = { onChange(!checked) }) {
        CtrlSwitch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    footer: String? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    GroupRow(title, null, icon, onClick = { open = true }) {
        Text(current.uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
    }
    if (open) {
        ChoiceSheet(title, options, selected, footer, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    footer: String? = null,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    CtrlBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).topDivider().clickable { onPick(value) }
                        .heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) CtrlColors.AccentText else CtrlColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Outlined.Check, stringResource(R.string.selected), Modifier.size(20.dp), tint = CtrlColors.AccentText)
                }
            }
            if (footer != null) {
                item {
                    Text(
                        footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
