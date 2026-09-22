package com.ticketkeep.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

val HomeBottomBarHorizontalMargin = 14.dp
val HomeBottomBarFloatGap = 10.dp
val HomeBottomBarContainerMinHeight = 72.dp
val HomeBottomBarCornerRadius = 36.dp

// Measured capsule height, excluding the system inset and floating gap.
val LocalHomeBottomBarHeight = compositionLocalOf { HomeBottomBarContainerMinHeight }

internal fun calculateHomeBottomBarListBottomPadding(navInset: Dp, containerHeight: Dp): Dp =
    maxOf(HomeBottomBarContainerMinHeight, containerHeight) +
        HomeBottomBarFloatGap + maxOf(0.dp, navInset) + 16.dp

@Composable
fun homeBottomBarListBottomPadding(navBarInsetsDp: Dp): Dp =
    calculateHomeBottomBarListBottomPadding(navBarInsetsDp, LocalHomeBottomBarHeight.current)

/**
 * Real backdrop blur: MainActivity supplies the page's Haze source, while only this
 * capsule samples it. Foreground labels/icons are drawn AFTER the material effect.
 * Unsupported/reduced-transparency rendering uses an opaque surface, not fake blur.
 */
@Composable
fun HomeBottomBar(
    currentRoute: String?,
    onSelectList: () -> Unit,
    onSelectSettings: () -> Unit,
    onAdd: () -> Unit,
    glassState: HazeState,
    blurEnabled: Boolean,
    onContainerHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val dark = darkTheme
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.background
    val tint = if (dark) Color(0xFF1E2F25).copy(alpha = 0.73f)
        else Color(0xFFF9FCF7).copy(alpha = 0.64f)
    val fallback = if (dark) Color(0xFF26392D) else Color(0xFFF3F7EF)
    val shape = RoundedCornerShape(HomeBottomBarCornerRadius)

    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding()) {
        val narrow = maxWidth < 360.dp
        val margin = if (narrow) 10.dp else HomeBottomBarHorizontalMargin
        Box(
            Modifier.padding(start = margin, end = margin, bottom = HomeBottomBarFloatGap),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onContainerHeightChanged(with(density) { it.height.toDp() }) }
                    .shadow(
                        12.dp, shape, clip = false,
                        ambientColor = Color(0xFF193626).copy(alpha = if (dark) 0.35f else 0.16f),
                        spotColor = Color(0xFF193626).copy(alpha = if (dark) 0.35f else 0.16f),
                    )
                    .clip(shape)
                    .then(
                        if (blurEnabled) Modifier.hazeChild(
                            state = glassState,
                            style = HazeStyle(
                                backgroundColor = surface,
                                tint = HazeTint(tint),
                                blurRadius = 22.dp,
                                noiseFactor = 0.015f,
                                fallbackTint = HazeTint(fallback),
                            ),
                        ) else Modifier.background(fallback),
                    )
                    .glassHighlights(dark, HomeBottomBarCornerRadius),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = HomeBottomBarContainerMinHeight)
                        .padding(horizontal = 7.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 7.dp),
                ) {
                    HomeTab(
                        currentRoute == Routes.LIST, onSelectList,
                        Icons.AutoMirrored.Outlined.ReceiptLong, "票证", dark, Modifier.weight(1f),
                    )
                    AddTicketButton(onAdd, dark, narrow, Modifier.weight(1.65f))
                    HomeTab(
                        currentRoute == Routes.SETTINGS, onSelectSettings,
                        Icons.Outlined.Person, "我的", dark, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Cached, density-aware directional reflection and a thin inner rim. No per-frame bitmaps. */
private fun Modifier.glassHighlights(dark: Boolean, radius: Dp): Modifier = drawWithCache {
    val reflection = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.07f else 0.22f),
        0.38f to Color.Transparent,
        0.7f to Color.White.copy(alpha = if (dark) 0.015f else 0.025f),
        1f to Color.White.copy(alpha = if (dark) 0.05f else 0.12f),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val edge = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.28f else 0.90f),
            Color.White.copy(alpha = if (dark) 0.07f else 0.22f),
            Color.White.copy(alpha = if (dark) 0.14f else 0.48f),
        ),
    )
    val stroke = 1.dp.toPx()
    val half = stroke / 2
    val curve = (minOf(radius.toPx(), size.height / 2) - half).coerceAtLeast(0f)
    onDrawWithContent {
        drawRect(reflection)
        drawContent()
        drawRoundRect(
            brush = edge,
            topLeft = Offset(half, half),
            size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(curve, curve),
            style = Stroke(stroke),
        )
    }
}

@Composable
private fun AddTicketButton(onClick: () -> Unit, dark: Boolean, narrow: Boolean, modifier: Modifier) {
    val compactLabel = LocalDensity.current.fontScale > 1.3f
    val shape = RoundedCornerShape(28.dp)
    val colors = if (dark) listOf(Color(0xFFD2E8B8), Color(0xFFB3D392))
        else listOf(Color(0xFF356B52), Color(0xFF214D39))
    Row(
        modifier.heightIn(min = 56.dp)
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.36f else 0.22f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "存一张票证" }
            .padding(horizontal = if (narrow) 7.dp else 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (dark) Color(0xFF182C20) else Color.White
        Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = content)
        Spacer(Modifier.width(4.dp))
        Text(
            if (compactLabel) "存票证" else "存一张票证",
            modifier = Modifier.weight(1f, fill = false), color = content,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = if (narrow) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    dark: Boolean,
    modifier: Modifier,
) {
    val color = when {
        dark && selected -> Color(0xFFE2F1D3)
        dark -> Color(0xFFD0DFCB)
        selected -> Color(0xFF224E39)
        else -> Color(0xFF475B4D)
    }
    val pill = if (dark) Color(0xFFADCE91).copy(alpha = 0.18f)
        else Color(0xFFDFECD8).copy(alpha = 0.80f)
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.heightIn(min = 56.dp).clip(shape)
            .then(if (selected) Modifier.background(pill).border(
                0.5.dp, Color.White.copy(alpha = if (dark) 0.13f else 0.65f), shape,
            ) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
        Spacer(Modifier.height(3.dp))
        Text(
            label, color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

fun shouldShowHomeBottomBar(route: String?): Boolean =
    route == Routes.LIST || route == Routes.SETTINGS
