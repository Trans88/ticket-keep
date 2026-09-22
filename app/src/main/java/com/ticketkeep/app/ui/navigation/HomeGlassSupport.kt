package com.ticketkeep.app.ui.navigation

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.HazeDefaults

private val transparencySettings = listOf(
    "accessibility_reduce_transparency", "reduce_transparency", "high_text_contrast_enabled",
)

fun shouldUseOpaqueGlassFallback(context: Context): Boolean {
    // Respect the library's reliability policy (including Android 12/API 31 exclusions).
    if (!HazeDefaults.blurEnabled()) return true
    return transparencySettings.any { key ->
        runCatching {
            Settings.Secure.getInt(context.contentResolver, key, 0) == 1 ||
                Settings.Global.getInt(context.contentResolver, key, 0) == 1
        }.getOrDefault(true)
    }
}

/** Re-check on resume and settings changes; do not freeze accessibility state at startup. */
@Composable
fun rememberHomeGlassBlurEnabled(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember(context, view) {
        mutableStateOf(view.isHardwareAccelerated && !shouldUseOpaqueGlassFallback(context))
    }
    DisposableEffect(context, view, lifecycle) {
        fun refresh() {
            enabled = view.isHardwareAccelerated && !shouldUseOpaqueGlassFallback(context)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        transparencySettings.forEach { key ->
            runCatching {
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
                context.contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
            }
        }
        val listener = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(listener)
        refresh()
        onDispose {
            lifecycle.removeObserver(listener)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return enabled
}
