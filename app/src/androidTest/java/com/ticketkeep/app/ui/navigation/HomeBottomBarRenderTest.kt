package com.ticketkeep.app.ui.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ticketkeep.app.ui.theme.TicketKeepTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic backdrop only: never reads a user's tickets or calls the backup service. */
class HomeBottomBarRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassFallbackDarkAndLargeText() {
        val blur = mutableStateOf(true)
        val dark = mutableStateOf(false)
        val large = mutableStateOf(false)
        val darkBackdrop = mutableStateOf(false)
        val route = mutableStateOf(Routes.LIST)
        var adds = 0
        var measured = 0.dp
        var screenDensity = 1f
        compose.setContent {
            val deviceDensity = LocalDensity.current
            screenDensity = deviceDensity.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, if (large.value) 2f else 1f)) {
                TicketKeepTheme(darkTheme = dark.value) {
                    val haze = remember { HazeState() }
                    Box(Modifier.size(320.dp, 460.dp).testTag("scene")) {
                        Canvas(Modifier.fillMaxSize().haze(haze)) {
                            // Fine, high-contrast vertical stripes reveal fake alpha-only glass.
                            val stripe = 8.dp.toPx()
                            for (i in 0..(size.width / stripe).toInt()) {
                                drawRect(
                                    if (darkBackdrop.value) Color(0xFF102030)
                                    else if (i % 2 == 0) Color(0xFF285A45) else Color(0xFFFAECD8),
                                    Offset(i * stripe, 0f), Size(stripe, size.height),
                                )
                            }
                        }
                        HomeBottomBar(
                            currentRoute = route.value,
                            onSelectList = { route.value = Routes.LIST },
                            onSelectSettings = { route.value = Routes.SETTINGS },
                            onAdd = { adds++ },
                            glassState = haze, blurEnabled = blur.value,
                            onContainerHeightChanged = { measured = it },
                            modifier = Modifier.align(Alignment.BottomCenter).testTag("dock"),
                            darkTheme = dark.value,
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("票证").assertIsSelected()
        compose.onNodeWithContentDescription("存一张票证").performClick()
        compose.runOnIdle { assertEquals(1, adds) }
        compose.onNodeWithText("我的").performClick().assertIsSelected()
        saveScreenshot("glass-light")
        val pixels = compose.onNodeWithTag("dock").captureToImage().toPixelMap()
        val y = (5 * screenDensity).toInt()
        val reds = (pixels.width * 35 / 100..pixels.width * 65 / 100).map { pixels[it, y].red }
        assertTrue("Fine stripes must be smoothed, not merely tinted", reds.max() - reds.min() < 0.08f)
        compose.runOnIdle { darkBackdrop.value = true }
        val changed = compose.onNodeWithTag("dock").captureToImage().toPixelMap()
        assertTrue("Backdrop changes must refresh the blur", kotlin.math.abs(
            changed[changed.width / 2, y].red - pixels[pixels.width / 2, y].red,
        ) > 0.03f)
        compose.runOnIdle { darkBackdrop.value = false }
        val glass = compose.onNodeWithTag("scene").captureToImage().asAndroidBitmap()
        compose.runOnIdle { blur.value = false }
        saveScreenshot("glass-fallback")
        val fallback = compose.onNodeWithTag("scene").captureToImage().asAndroidBitmap()
        assertTrue("Backdrop effect must differ from opaque fallback", !glass.sameAs(fallback))
        compose.runOnIdle { blur.value = true; dark.value = true }
        saveScreenshot("glass-dark")
        compose.runOnIdle { large.value = true }
        saveScreenshot("glass-large-text")
        compose.runOnIdle { assertTrue("Measured height must grow with 200% text", measured > 72.dp) }
        compose.onNodeWithContentDescription("存一张票证").performClick()
        compose.runOnIdle { assertEquals(2, adds) }
    }

    private fun saveScreenshot(name: String) {
        val bitmap = compose.onNodeWithTag("scene").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
