package dev.d3v.notificationsaver

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5B4B),
    onPrimary = Color(0xFFF7F5EF),
    primaryContainer = Color(0xFFD7E6DE),
    onPrimaryContainer = Color(0xFF173429),
    secondary = Color(0xFF5E6762),
    onSecondary = Color(0xFFF7F5EF),
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF1C211F),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1C211F),
    surfaceVariant = Color(0xFFE9E4D8),
    onSurfaceVariant = Color(0xFF5E6762),
    outline = Color(0xFFC9C1B3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB8A5),
    onPrimary = Color(0xFF142720),
    primaryContainer = Color(0xFF213C32),
    onPrimaryContainer = Color(0xFFDAEADF),
    secondary = Color(0xFFAAB4AF),
    onSecondary = Color(0xFF18201C),
    background = Color(0xFF111513),
    onBackground = Color(0xFFEEF2EE),
    surface = Color(0xFF171C19),
    onSurface = Color(0xFFEEF2EE),
    surfaceVariant = Color(0xFF222A26),
    onSurfaceVariant = Color(0xFFAAB4AF),
    outline = Color(0xFF45504A),
)

@Composable
fun NotificationSaverTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (useDarkTheme) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
