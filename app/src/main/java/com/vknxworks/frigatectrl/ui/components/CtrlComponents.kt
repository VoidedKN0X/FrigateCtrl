@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vknxworks.frigatectrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlFonts
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType

// The shared Ctrl / Signal building blocks: top bars, cards, list rows, the floating bottom bar,
// switches, buttons, chips, progress bars, dialogs and sheets.

/** A 1 dp line along the top edge: list rows are separated this way. */
fun Modifier.topDivider(color: Color = CtrlColors.Divider): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

/** Mono, upper-case section label ("PRIVACY", "CAMERAS · 4"). */
@Composable
fun CtrlSectionLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "FRIGATE/CTRL" with the slash in the accent colour. */
@Composable
fun CtrlWordmark(name: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier,
    )
}

/** The 8 dp square status dot. */
@Composable
fun CtrlStatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(color))
}

/**
 * The 44 dp profile picture: a rounded square with the login's initial (or a person icon), and a small
 * accent square at the bottom right while connected. Tapping it opens the profile / settings.
 */
@Composable
fun CtrlAvatar(
    initial: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    connected: Boolean = false,
) {
    Box(modifier.size(44.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShape.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlOutline, CtrlShape.Control)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            if (initial == null) Icon(Icons.Outlined.Person, null, Modifier.size(20.dp), tint = CtrlColors.Text)
            else Text(initial, fontFamily = CtrlFonts.Grotesk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CtrlColors.Text)
        }
        if (connected) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(14.dp)
                    .clip(RoundedCornerShape(5.dp)).background(CtrlColors.Background).padding(2.dp)
                    .clip(RoundedCornerShape(3.dp)).background(CtrlColors.Accent)
            )
        }
    }
}

/** Top bar of the main tabs: wordmark on the left, the profile picture on the right. No colour fill. */
@Composable
fun CtrlTopBar(name: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlWordmark(name, Modifier.weight(1f))
        trailing()
    }
}

/** The 44 dp square button used for back, close and header actions. [accent] fills it with the accent. */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    tint: Color = if (accent) CtrlColors.OnAccent else CtrlColors.Text,
    enabled: Boolean = true,
    badge: Int? = null,
) {
    Box(modifier.size(44.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShape.Control)
                .background(if (accent) CtrlColors.Accent else CtrlColors.Surface)
                .then(if (accent) Modifier else Modifier.border(1.dp, CtrlColors.ControlOutline, CtrlShape.Control))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint) }
        if (badge != null && badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).heightIn(min = 18.dp).widthIn(min = 18.dp)
                    .clip(RoundedCornerShape(6.dp)).background(CtrlColors.Accent).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${badge.coerceAtMost(99)}", color = CtrlColors.OnAccent,
                    fontFamily = CtrlFonts.Mono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Top bar of a detail screen: a square back button and the title in Space Grotesk, an optional
 * line under it, and actions on the right.
 */
@Composable
fun CtrlSubTopBar(
    title: String,
    onBack: () -> Unit,
    backIcon: ImageVector,
    backDescription: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlSquareButton(backIcon, backDescription, onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.invoke()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Secondary card: surface fill, 1 dp border, 22 dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CtrlShape.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.Outline, CtrlShape.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * The hero card: the one most important thing on a main screen. Accent fill, dark content,
 * a mono label row on top and (usually) a giant number below.
 */
@Composable
fun CtrlHeroCard(
    label: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides CtrlColors.OnAccent) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(CtrlShape.Hero)
                .background(CtrlColors.Accent)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(), style = CtrlType.Label, fontWeight = FontWeight.SemiBold, color = CtrlColors.OnAccent,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (trailingLabel != null) Text(
                    trailingLabel.uppercase(), style = CtrlType.Label, fontWeight = FontWeight.SemiBold,
                    color = CtrlColors.OnAccent, maxLines = 1,
                )
            }
            content()
        }
    }
}

/**
 * A plain list row, at least 52 dp tall, with a 1 dp divider on top: muted mono meta on the left,
 * the title, and a mono status with a square dot on the right (accent when [active]).
 */
@Composable
fun CtrlListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    subtitle: String? = null,
    status: String? = null,
    statusColor: Color = CtrlColors.AccentText,
    showDot: Boolean = status != null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (divider) Modifier.topDivider() else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        if (meta != null) Text(meta, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = CtrlFonts.Grotesk, color = CtrlColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = CtrlType.Mono, fontSize = 11.sp, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) Text(status, style = CtrlType.Label, letterSpacing = 0.1.sp, color = statusColor, maxLines = 1)
        if (showDot) CtrlStatusDot(statusColor)
        trailing?.invoke()
    }
}

/**
 * Rounded-rectangle switch, 52 × 32 dp: accent track with a dark knob when on, grey when off.
 * Announced as a switch to accessibility services.
 */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobX by animateDpAsState(if (checked) 20.dp else 0.dp, label = "knob")
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchTrackOff, label = "track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.SwitchKnobOff, label = "knobColor")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.4f)
            .background(track)
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = knobX).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
    }
}

/** Primary button: accent fill, dark text, 56 dp tall by default. */
@Composable
fun CtrlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
    containerColor: Color = CtrlColors.Accent,
    contentColor: Color = CtrlColors.OnAccent,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CtrlShape.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Inactive,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = modifier.height(height),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = contentColor)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontFamily = CtrlFonts.Grotesk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Secondary button: surface fill and a 1 dp border. */
@Composable
fun CtrlSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CtrlShape.Control,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CtrlColors.Surface, contentColor = CtrlColors.Text),
        border = BorderStroke(1.dp, CtrlColors.ControlOutline),
        contentPadding = contentPadding,
        modifier = modifier,
        content = content,
    )
}

/** Filter / preset chip: 36 dp tall rounded rectangle, accent when selected. */
@Composable
fun CtrlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) CtrlColors.Accent else CtrlColors.Surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, CtrlColors.ControlOutline, shape))
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) CtrlColors.OnAccent else CtrlColors.Text
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, fontFamily = CtrlFonts.Grotesk, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 6 dp progress bar: white fill (accent for the primary metric) on a dark track. */
@Composable
fun CtrlProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = CtrlColors.ProgressFill) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CtrlColors.ProgressTrack)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** One destination of [CtrlBottomBar]. */
data class CtrlTab(val label: String, val icon: ImageVector)

/** Height of the floating bottom bar, so screens can leave room for it. */
val CtrlBottomBarHeight: Dp = 64.dp

/** Distance between the floating bottom bar and the bottom of the screen (above the navigation bar). */
val CtrlBottomBarMargin: Dp = 28.dp

/**
 * Floating bottom navigation: 64 dp tall, 22 dp corners, translucent surface and a 1 dp border.
 * The selected tab is an accent rounded rectangle with its icon and label; the others are icons only.
 */
@Composable
fun CtrlBottomBar(tabs: List<CtrlTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(CtrlBottomBarHeight)
            .clip(CtrlShape.Card)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlOutline, CtrlShape.Card)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSel = i == selected
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .height(48.dp)
                    .then(if (isSel) Modifier else Modifier.width(48.dp))
                    .clip(shape)
                    .background(if (isSel) CtrlColors.Accent else Color.Transparent)
                    .selectable(selected = isSel, role = Role.Tab) {
                        if (!isSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }
                    .animateContentSize()
                    .padding(horizontal = if (isSel) 18.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    tab.icon, if (isSel) null else tab.label,
                    Modifier.size(if (isSel) 18.dp else 20.dp),
                    tint = if (isSel) CtrlColors.OnAccent else CtrlColors.Muted,
                )
                if (isSel) {
                    Spacer(Modifier.width(8.dp))
                    Text(tab.label, color = CtrlColors.OnAccent, fontFamily = CtrlFonts.Grotesk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** Dialog in the series style: surface fill, 26 dp corners, 1 dp border. */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = Modifier.border(1.dp, CtrlColors.Outline, CtrlShape.Hero),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShape.Hero,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.AccentText,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
    )
}

/** Bottom sheet in the series style: surface fill, 26 dp top corners, 1 dp border. */
@Composable
fun CtrlBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.border(1.dp, CtrlColors.Outline, CtrlShape.Sheet),
        shape = CtrlShape.Sheet,
        containerColor = CtrlColors.Surface,
        contentColor = CtrlColors.Text,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CtrlColors.ControlOutline) },
        content = content,
    )
}

/** Text field colours: surface fill, 1 dp border that turns accent with the cursor when focused. */
@Composable
fun ctrlFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.AccentText,
    unfocusedBorderColor = CtrlColors.ControlOutline,
    cursorColor = CtrlColors.AccentText,
    focusedLabelColor = CtrlColors.AccentText,
    unfocusedLabelColor = CtrlColors.Muted,
    focusedLeadingIconColor = CtrlColors.AccentText,
    unfocusedLeadingIconColor = CtrlColors.Muted,
    focusedTrailingIconColor = CtrlColors.Text,
    unfocusedTrailingIconColor = CtrlColors.Muted,
)
