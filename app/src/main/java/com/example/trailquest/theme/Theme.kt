package com.example.trailquest.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenDarkPrimary,
    onPrimary = Color(0xFF0A200D),
    primaryContainer = ForestGreenDarkContainer,
    onPrimaryContainer = OnForestGreenDarkContainer,
    secondary = SageDarkSecondary,
    onSecondary = Color(0xFF1F3320),
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = Color(0xFFD6E8D3),
    tertiary = WarmEarthDarkTertiary,
    onTertiary = Color(0xFF422C0E),
    background = DarkForestBackground,
    onBackground = DarkOnSurface,
    surface = DarkForestSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF283129),
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF2B332B)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = ForestGreenContainer,
    onPrimaryContainer = OnForestGreenContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = Color(0xFF142415),
    tertiary = WarmEarthTertiary,
    onTertiary = Color.White,
    background = WarmIvoryBackground,
    onBackground = LightOnSurface,
    surface = WarmIvorySurface,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFEFF2EB),
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFE4E9E1)
)

@Composable
fun TrailQuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

