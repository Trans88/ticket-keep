package com.ticketkeep.app.ui.navigation

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 主页悬浮玻璃底栏 v2.2 — 设计桥 SoT：票证 | 「+ 存一张票证」动作 | 我的。
 *
 * ## 模糊 / 玻璃策略（重要）
 * - 视觉意图：导航胶囊「模糊」其背后滚动内容（对齐 CSS backdrop-filter）。
 * - **当前实现**：半透明材质色 + 细高光描边 + 柔和外阴影；**不是**每帧截图模糊，
 *   也未引入 Haze 等第三方依赖（Compose 无可靠跨版本 backdrop-filter）。
 * - **回退**：API &lt; 31，或可读到 reduce-transparency / 探测失败时，改用高不透明
 *   `#F3F7EF` / `#26392D`，仍保留左右外距、圆角与阴影。
 * - 图标与文字本身禁止 blur。
 *
 * 中心按钮是 onAdd 动作，不是 Tab。点击区 ≥48dp。
 */

/** 相对屏幕左右的外距（窄屏可再收）。 */
val HomeBottomBarHorizontalMargin = 14.dp

/** 相对 navigationBars 顶部再浮起的间隙。 */
val HomeBottomBarFloatGap = 10.dp

/** 胶囊内容区最小高度（不含 nav inset / floatGap）。 */
val HomeBottomBarContainerMinHeight = 72.dp

/** 胶囊圆角（接近半胶囊）。 */
val HomeBottomBarCornerRadius = 36.dp

private val GlassLight = Color(0xFFF9FCF7)
private val GlassDark = Color(0xFF1E2F25)
private val GlassOpaqueLight = Color(0xFFF3F7EF)
private val GlassOpaqueDark = Color(0xFF26392D)
private val TabPillLight = Color(0xDFDFECD8) // ~ rgba(223,236,216,.8)
private val TabPillDark = Color(0x2EADCE91) // ~ rgba(173,206,145,.18)
private val AddGradientTop = Color(0xFF2F6B52)
private val AddGradientBottom = Color(0xFF285A45)
private val AddDarkBg = Color(0xFFD8ECAC)
private val AddDarkFg = Color(0xFF18231D)

/**
 * 列表 / 我的 底部留白估算：栏高 + 内边 + floatGap + navBars inset + 16dp 余量。
 * MainActivity 已 contentWindowInsets=0，请把 [navBarInsetsDp] 传入，勿在 LazyColumn 外再套一层
 * navigationBarsPadding 以免重复。
 */
fun homeBottomBarListBottomPadding(navBarInsetsDp: Dp): Dp =
    HomeBottomBarContainerMinHeight +
        14.dp + // 胶囊上下内边约 7+7
        HomeBottomBarFloatGap +
        navBarInsetsDp +
        16.dp

/** API&lt;31 或系统「减少透明度」类开关开启时，用高不透明回退。 */
fun shouldUseOpaqueGlassFallback(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return try {
        val cr = context.contentResolver
        val keys = listOf(
            "accessibility_reduce_transparency",
            "reduce_transparency",
            "high_text_contrast_enabled",
        )
        keys.any { key ->
            try {
                Settings.Secure.getInt(cr, key, 0) == 1 ||
                    Settings.Global.getInt(cr, key, 0) == 1
            } catch (_: Throwable) {
                false
            }
        }
    } catch (_: Throwable) {
        true
    }
}

@Composable
fun HomeBottomBar(
    currentRoute: String?,
    onSelectList: () -> Unit,
    onSelectSettings: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listSelected = currentRoute == Routes.LIST
    val settingsSelected = currentRoute == Routes.SETTINGS
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val opaqueFallback = remember(context) { shouldUseOpaqueGlassFallback(context) }
    val narrow = LocalConfiguration.current.screenWidthDp < 360
    val horizontalMargin = if (narrow) 10.dp else HomeBottomBarHorizontalMargin

    val glassColor = when {
        opaqueFallback && dark -> GlassOpaqueDark
        opaqueFallback && !dark -> GlassOpaqueLight
        dark -> GlassDark.copy(alpha = 0.73f)
        else -> GlassLight.copy(alpha = 0.64f)
    }
    val borderColor = if (dark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val shape = RoundedCornerShape(HomeBottomBarCornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = horizontalMargin,
                end = horizontalMargin,
                bottom = HomeBottomBarFloatGap,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    ambientColor = Color(0xFF273B29).copy(alpha = if (dark) 0.45f else 0.18f),
                    spotColor = Color(0xFF273B29).copy(alpha = if (dark) 0.40f else 0.16f),
                )
                .clip(shape)
                .background(glassColor, shape)
                .border(width = 1.dp, color = borderColor, shape = shape),
        ) {
            // 顶部细高光（不可点），模拟 inset highlight
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (dark) 0.10f else 0.28f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = 48f,
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HomeBottomBarContainerMinHeight)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeTab(
                    selected = listSelected,
                    onClick = onSelectList,
                    selectedIcon = Icons.Filled.ReceiptLong,
                    unselectedIcon = Icons.Outlined.ReceiptLong,
                    label = "票证",
                    dark = dark,
                    modifier = Modifier.weight(1f),
                )
                AddTicketButton(onClick = onAdd, dark = dark)
                HomeTab(
                    selected = settingsSelected,
                    onClick = onSelectSettings,
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    label = "我的",
                    dark = dark,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddTicketButton(
    onClick: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    val content = if (dark) AddDarkFg else Color.White
    val solid = if (dark) AddDarkBg else AddGradientBottom
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .defaultMinSize(minWidth = 113.dp, minHeight = 48.dp)
            .clip(shape)
            .background(
                brush = if (dark) {
                    Brush.verticalGradient(listOf(solid, solid))
                } else {
                    Brush.verticalGradient(listOf(AddGradientTop, AddGradientBottom))
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "存一张票证"
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = content,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "存一张票证",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HomeTab(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pill = if (dark) TabPillDark else TabPillLight
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (selected) {
                    Modifier.background(pill, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = color,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 主 Tab（list / settings）显示底栏；详情/编辑/付费墙/备份/隐私/批量全屏时隐藏。 */
fun shouldShowHomeBottomBar(route: String?): Boolean =
    route == Routes.LIST || route == Routes.SETTINGS
