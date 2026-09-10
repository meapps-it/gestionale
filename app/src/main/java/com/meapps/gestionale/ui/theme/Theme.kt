package com.meapps.gestionale.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Navy = Color(0xFF0F172A)
val ActionBlue = Color(0xFF2563EB)
val AppBackground = Color(0xFFF8FAFC)
val Line = Color(0xFFBCC6D4)
val Positive = Color(0xFF16A34A)
val Warning = Color(0xFFF59E0B)
val Danger = Color(0xFFDC2626)

private val Light = lightColorScheme(primary=ActionBlue, secondary=Warning, background=AppBackground,
    surface=Color.White, error=Danger, onPrimary=Color.White, onBackground=Navy, onSurface=Navy)
private val Dark = darkColorScheme(primary=Color(0xFF75A7FF), background=Color(0xFF0B1120),
    surface=Color(0xFF172033), onBackground=Color.White, onSurface=Color.White)

@Composable fun GestionaleTheme(mode:String="Sistema", content: @Composable () -> Unit) {
    val dark = when(mode) { "Scuro"->true;"Chiaro"->false;else->isSystemInDarkTheme() }
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = Navy.value.toInt()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    // Typography() keeps FontFamily.Default: Android can therefore expose the device font.
    MaterialTheme(colorScheme=if(dark) Dark else Light, typography=Typography(), content=content)
}
