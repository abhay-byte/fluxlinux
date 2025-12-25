package com.ivarna.fluxlinux.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = FluxAccentCyan,
    secondary = FluxAccentMagenta,
    tertiary = FluxAccentCyan,
    background = FluxBackgroundStart,
    surface = GlassWhiteLow,
    onPrimary = TextWhite,
    onSecondary = TextWhite,
    onTertiary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite,
)

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = FluxAccentCyan,
    secondary = FluxAccentMagenta,
    tertiary = FluxAccentCyan,
    background = androidx.compose.ui.graphics.Color(0xFFF5F5F7), // Light Gray
    surface = androidx.compose.ui.graphics.Color.White,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.Black,
    onSurface = androidx.compose.ui.graphics.Color.Black,
)

@Composable
fun FluxLinuxTheme(
    themeMode: com.ivarna.fluxlinux.core.utils.ThemeMode = com.ivarna.fluxlinux.core.utils.ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    
    val colorScheme = when (themeMode) {
        com.ivarna.fluxlinux.core.utils.ThemeMode.DARK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(context) else DarkColorScheme
        com.ivarna.fluxlinux.core.utils.ThemeMode.LIGHT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) androidx.compose.material3.dynamicLightColorScheme(context) else LightColorScheme
        com.ivarna.fluxlinux.core.utils.ThemeMode.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColorScheme else LightColorScheme
            }
        }
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(context) else DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Fully transparent status bar for immersion
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Update status bar icons visibility
            val isDark = when (themeMode) {
                com.ivarna.fluxlinux.core.utils.ThemeMode.DARK -> true
                com.ivarna.fluxlinux.core.utils.ThemeMode.LIGHT -> false
                com.ivarna.fluxlinux.core.utils.ThemeMode.SYSTEM -> systemDark
                else -> true // All custom themes (Gruvbox, Nord, etc.) are treated as Dark
            }
            
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
