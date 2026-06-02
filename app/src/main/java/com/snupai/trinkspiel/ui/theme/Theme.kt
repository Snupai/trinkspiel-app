package com.snupai.trinkspiel.ui.theme

import android.app.Activity
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.snupai.trinkspiel.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = SeemopsTeal,
    onPrimary = SeemopsOnTeal,
    primaryContainer = SeemopsTealContainer,
    onPrimaryContainer = SeemopsOnTealContainer,
    secondary = SeemopsBerry,
    onSecondary = SeemopsOnBerry,
    secondaryContainer = SeemopsBerryContainer,
    onSecondaryContainer = SeemopsOnBerryContainer,
    tertiary = SeemopsAmber,
    onTertiary = SeemopsOnAmber,
    tertiaryContainer = SeemopsAmberContainer,
    onTertiaryContainer = SeemopsOnAmberContainer,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = SeemopsTealDark,
    onPrimary = SeemopsOnTealDark,
    primaryContainer = SeemopsTealContainerDark,
    onPrimaryContainer = SeemopsOnTealContainerDark,
    secondary = SeemopsBerryDark,
    onSecondary = SeemopsOnBerryDark,
    secondaryContainer = SeemopsBerryContainerDark,
    onSecondaryContainer = SeemopsOnBerryContainerDark,
    tertiary = SeemopsAmberDark,
    onTertiary = SeemopsOnAmberDark,
    tertiaryContainer = SeemopsAmberContainerDark,
    onTertiaryContainer = SeemopsOnAmberContainerDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

private val SeemopsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun TrinkspielTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColors: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SeemopsShapes,
        content = content
    )
}

@Composable
fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean =
    when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
