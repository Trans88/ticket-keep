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
    primary = MintPrimary,
    onPrimary = MintOnPrimary,
    primaryContainer = MintPrimaryContainer,
    onPrimaryContainer = MintOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = MintPrimary,
    onTertiary = MintOnPrimary,
    tertiaryContainer = MintPrimaryContainer,
    onTertiaryContainer = MintOnPrimaryContainer,
    background = PaperBackground,
    onBackground = PaperOnBackground,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperOnSurfaceVariant,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
    error = ErrorRed,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    scrim = ScrimBlack,
)

private val DarkColors = darkColorScheme(
    primary = InversePrimary,
    onPrimary = Color(0xFF134E4A),
    primaryContainer = Color(0xFF0F766E),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = InversePrimary,
    background = Color(0xFF121514),
    onBackground = Color(0xFFE8EFED),
    surface = Color(0xFF1A1E1D),
    onSurface = Color(0xFFE8EFED),
    surfaceVariant = Color(0xFF2E3231),
    onSurfaceVariant = Color(0xFFB7C4C0),
    outline = Color(0xFF8A9692),
    outlineVariant = Color(0xFF3E4543),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    scrim = ScrimBlack,
)

/** 纸感圆角：卡片 16 / 控件 12 */
val TicketKeepShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun TicketKeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 默认关闭动态色，保证薄荷品牌不被 Material You 洗掉 */
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
