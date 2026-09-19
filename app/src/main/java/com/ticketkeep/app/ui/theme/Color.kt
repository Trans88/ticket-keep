package com.ticketkeep.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v2 语义色（tokens.json）：森林绿品牌 + 暖白纸感；含浅/深色。
 * 保留旧薄荷别名作兼容，新代码请用 Light* / Dark* 或 MaterialTheme。
 */

// —— Light ——
val LightBackground = Color(0xFFF7F8F4)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF202D27)
val LightOnSurfaceVariant = Color(0xFF67736B)
val LightPrimary = Color(0xFF285A45)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEAF0E6)
val LightOnPrimaryContainer = Color(0xFF285A45)
val LightAccent = Color(0xFFD8ECAC)
val LightOutlineVariant = Color(0xFFE3E8E0)
val LightControlOutline = Color(0xFF849283)
val LightWarningContainer = Color(0xFFFAECD8)
val LightOnWarningContainer = Color(0xFF955322)
val LightError = Color(0xFFB13737)
val LightErrorContainer = Color(0xFFFBE8E7)
val LightOnErrorContainer = Color(0xFF8D2525)
val LightHeroBackground = Color(0xFF285A45)
val LightOnHero = Color(0xFFF7FAEF)
val LightOnHeroSecondary = Color(0xFFDBE5D8)
val LightSurfaceVariant = Color(0xFFEAF0E6)
val LightOutline = Color(0xFF849283)

// —— Dark ——
val DarkBackground = Color(0xFF18231D)
val DarkSurface = Color(0xFF223128)
val DarkOnSurface = Color(0xFFEDF2E8)
val DarkOnSurfaceVariant = Color(0xFFACB9A9)
val DarkPrimary = Color(0xFFBDDA9F)
val DarkOnPrimary = Color(0xFF18231D)
val DarkPrimaryContainer = Color(0xFF344934)
val DarkOnPrimaryContainer = Color(0xFFD8ECAC)
val DarkAccent = Color(0xFFD8ECAC)
val DarkOutlineVariant = Color(0xFF3C4D3F)
val DarkControlOutline = Color(0xFF879783)
val DarkWarningContainer = Color(0xFF493622)
val DarkOnWarningContainer = Color(0xFFF0C086)
val DarkError = Color(0xFFFFB4AB)
val DarkErrorContainer = Color(0xFF552C2C)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkHeroBackground = Color(0xFF344934)
val DarkOnHero = Color(0xFFF7FAEF)
val DarkOnHeroSecondary = Color(0xFFDBE5D8)
val DarkSurfaceVariant = Color(0xFF344934)
val DarkOutline = Color(0xFF879783)

val ScrimBlack = Color(0xFF000000)

/** 语义：成功（保修中 >30 天）— 映射 primaryContainer 体系 */
val Success = LightPrimary
val OnSuccess = LightOnPrimary
val SuccessContainer = LightPrimaryContainer
val OnSuccessContainer = LightOnPrimaryContainer

val Warning = LightOnWarningContainer
val OnWarning = Color(0xFFFFFFFF)
val WarningContainer = LightWarningContainer
val OnWarningContainer = LightOnWarningContainer

// —— 兼容旧引用名（逐步迁移）——
@Deprecated("Use LightPrimary", ReplaceWith("LightPrimary"))
val MintPrimary = LightPrimary
@Deprecated("Use LightOnPrimary", ReplaceWith("LightOnPrimary"))
val MintOnPrimary = LightOnPrimary
@Deprecated("Use LightPrimaryContainer", ReplaceWith("LightPrimaryContainer"))
val MintPrimaryContainer = LightPrimaryContainer
@Deprecated("Use LightOnPrimaryContainer", ReplaceWith("LightOnPrimaryContainer"))
val MintOnPrimaryContainer = LightOnPrimaryContainer

val SlateSecondary = LightOnSurfaceVariant
val SlateOnSecondary = LightOnPrimary
val SlateSecondaryContainer = LightSurfaceVariant
val SlateOnSecondaryContainer = LightOnSurface

val PaperBackground = LightBackground
val PaperOnBackground = LightOnSurface
val PaperSurface = LightSurface
val PaperOnSurface = LightOnSurface
val PaperSurfaceVariant = LightSurfaceVariant
val PaperOnSurfaceVariant = LightOnSurfaceVariant
val PaperOutline = LightOutline
val PaperOutlineVariant = LightOutlineVariant

val InverseSurface = DarkSurface
val InverseOnSurface = DarkOnSurface
val InversePrimary = DarkPrimary

val ErrorRed = LightError
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = LightErrorContainer
val OnErrorContainer = LightOnErrorContainer
