package com.ticketkeep.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightOnSurfaceVariant,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightAccent,
    onTertiaryContainer = LightOnPrimaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
    inversePrimary = DarkPrimary,
    error = LightError,
    onError = Color(0xFFFFFFFF),
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    scrim = ScrimBlack,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkPrimaryContainer,
    onTertiaryContainer = DarkOnPrimaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    inversePrimary = LightPrimary,
    error = DarkError,
    onError = DarkOnPrimary,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = ScrimBlack,
)

/**
 * v2 形状：badge 7 / field 12 / button 15 / ticket 18 / card 22 / sheet 26。
 * Material shapes 映射：extraSmall=badge、small=field、medium=ticket、large=card、extraLarge=sheet。
 */
val TicketKeepShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** 圆角 token 便捷值（Compose 组件直接用） */
object TicketKeepRadius {
    val badge = 7.dp
    val field = 12.dp
    val button = 15.dp
    val ticket = 18.dp
    val card = 22.dp
    val sheet = 26.dp
}

object TicketKeepSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val page = 20.dp
    val section = 24.dp
    val large = 32.dp
}

@Composable
fun TicketKeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 默认关闭动态色，保证品牌色不被 Material You 洗掉 */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TicketKeepShapes,
        content = content,
    )
}
