package com.doggy.clip_manager.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.doggy.clip_manager.core.designsystem.R

@Composable
private fun lightColors(): ColorScheme = lightColorScheme(
    primary = colorResource(R.color.clip_teal_40),
    onPrimary = colorResource(R.color.clip_grey_99),
    primaryContainer = colorResource(R.color.clip_teal_90),
    onPrimaryContainer = colorResource(R.color.clip_teal_10),
    secondary = colorResource(R.color.clip_amber_40),
    onSecondary = colorResource(R.color.clip_grey_99),
    secondaryContainer = colorResource(R.color.clip_amber_90),
    onSecondaryContainer = colorResource(R.color.clip_amber_10),
    error = colorResource(R.color.clip_red_40),
    onError = colorResource(R.color.clip_grey_99),
    errorContainer = colorResource(R.color.clip_red_90),
    onErrorContainer = colorResource(R.color.clip_red_10),
    background = colorResource(R.color.clip_grey_99),
    onBackground = colorResource(R.color.clip_grey_10),
    surface = colorResource(R.color.clip_grey_99),
    onSurface = colorResource(R.color.clip_grey_10),
    surfaceVariant = colorResource(R.color.clip_teal_grey_90),
    onSurfaceVariant = colorResource(R.color.clip_teal_grey_30),
    outline = colorResource(R.color.clip_teal_grey_50),
    inverseSurface = colorResource(R.color.clip_grey_20),
    inverseOnSurface = colorResource(R.color.clip_grey_95),
    inversePrimary = colorResource(R.color.clip_teal_80),
)

@Composable
private fun darkColors(): ColorScheme = darkColorScheme(
    primary = colorResource(R.color.clip_teal_80),
    onPrimary = colorResource(R.color.clip_teal_20),
    primaryContainer = colorResource(R.color.clip_teal_30),
    onPrimaryContainer = colorResource(R.color.clip_teal_90),
    secondary = colorResource(R.color.clip_amber_80),
    onSecondary = colorResource(R.color.clip_amber_20),
    secondaryContainer = colorResource(R.color.clip_amber_30),
    onSecondaryContainer = colorResource(R.color.clip_amber_90),
    error = colorResource(R.color.clip_red_80),
    onError = colorResource(R.color.clip_red_20),
    errorContainer = colorResource(R.color.clip_red_30),
    onErrorContainer = colorResource(R.color.clip_red_90),
    background = colorResource(R.color.clip_grey_10),
    onBackground = colorResource(R.color.clip_grey_90),
    surface = colorResource(R.color.clip_grey_10),
    onSurface = colorResource(R.color.clip_grey_90),
    surfaceVariant = colorResource(R.color.clip_teal_grey_30),
    onSurfaceVariant = colorResource(R.color.clip_teal_grey_80),
    outline = colorResource(R.color.clip_teal_grey_60),
    inverseSurface = colorResource(R.color.clip_grey_90),
    inverseOnSurface = colorResource(R.color.clip_grey_10),
    inversePrimary = colorResource(R.color.clip_teal_40),
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
        darkTheme -> darkColors()
        else -> lightColors()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
