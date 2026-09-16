package com.doggy.clip_manager.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Teal40, onPrimary = Grey99, primaryContainer = Teal90, onPrimaryContainer = Teal10,
    secondary = Amber40, onSecondary = Grey99, secondaryContainer = Amber90, onSecondaryContainer = Amber10,
    error = Red40, onError = Grey99, errorContainer = Red90, onErrorContainer = Red10,
    background = Grey99, onBackground = Grey10, surface = Grey99, onSurface = Grey10,
    surfaceVariant = TealGrey90, onSurfaceVariant = TealGrey30, outline = TealGrey50,
    inverseSurface = Grey20, inverseOnSurface = Grey95, inversePrimary = Teal80,
)

private val DarkColors = darkColorScheme(
    primary = Teal80, onPrimary = Teal20, primaryContainer = Teal30, onPrimaryContainer = Teal90,
    secondary = Amber80, onSecondary = Amber20, secondaryContainer = Amber30, onSecondaryContainer = Amber90,
    error = Red80, onError = Red20, errorContainer = Red30, onErrorContainer = Red90,
    background = Grey10, onBackground = Grey90, surface = Grey10, onSurface = Grey90,
    surfaceVariant = TealGrey30, onSurfaceVariant = TealGrey80, outline = TealGrey60,
    inverseSurface = Grey90, inverseOnSurface = Grey10, inversePrimary = Teal40,
)

@Composable
fun ClipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
    MaterialTheme(colorScheme = colorScheme, content = content)
}
