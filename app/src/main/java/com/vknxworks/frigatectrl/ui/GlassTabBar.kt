package com.vknxworks.frigatectrl.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

data class GlassTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Height of the floating bar, so screens can leave room for it at the bottom of their lists. */
val GlassTabBarHeight: Dp = 64.dp

/**
 * Floating "island" tab bar: a frosted-glass pill that blurs whatever scrolls behind it
 * (Material 3 colours, iOS-style glass). A tonal capsule springs between tabs.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 96.dp,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    val inset = 6.dp
    val indicatorX by animateDpAsState(
        itemWidth * selected,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "indicator",
    )

    Box(
        modifier
            .height(GlassTabBarHeight)
            .clip(shape)
            .hazeEffect(state = hazeState, style = HazeMaterials.thin(cs.surfaceContainer))
            // A faint highlight along the rim sells the glass edge.
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))),
                shape,
            )
            .padding(inset),
    ) {
        // Sliding capsule behind the selected tab.
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(shape)
                .background(cs.secondaryContainer.copy(alpha = 0.9f))
        )
        Row(horizontalArrangement = Arrangement.Start) {
            tabs.forEachIndexed { i, tab ->
                val isSel = i == selected
                val tint by animateColorAsState(
                    if (isSel) cs.onSecondaryContainer else cs.onSurfaceVariant, label = "tint",
                )
                Column(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(shape)
                        .selectable(
                            selected = isSel,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = {
                                if (!isSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (isSel) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(
                        tab.label, color = tint, fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
