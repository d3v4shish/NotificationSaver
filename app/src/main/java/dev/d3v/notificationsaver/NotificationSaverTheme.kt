package dev.d3v.notificationsaver

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared semantic colors. UI chrome stays neutral; these colors communicate state. */
internal object UtilityColors {
    val Background = Color(0xFFF3F0E8)
    val Surface = Color(0xFFFAF8F2)
    val Border = Color(0xFFC8C3B8)
    val StrongBorder = Color(0xFF24231F)
    val TextPrimary = Color(0xFF181816)
    val TextSecondary = Color(0xFF67645E)
    val Interactive = Color(0xFF2563EB)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFDC2626)
    val Intelligent = Color(0xFF8B5CF6)
    val Disabled = Color(0xFF9CA3AF)
    val SuccessContainer = Color(0xFFE7F6EB)
    val WarningContainer = Color(0xFFFFF1D8)
    val ErrorContainer = Color(0xFFFDE8E7)
    val IntelligentContainer = Color(0xFFF0EAFF)
}

/** Lets selection chrome stay neutral when the app is explicitly set to dark mode. */
internal val LocalUtilityDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = UtilityColors.Interactive,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4ECFF),
    onPrimaryContainer = Color(0xFF143C96),
    secondary = UtilityColors.TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE6DD),
    onSecondaryContainer = UtilityColors.TextPrimary,
    tertiary = UtilityColors.Intelligent,
    onTertiary = Color.White,
    tertiaryContainer = UtilityColors.IntelligentContainer,
    onTertiaryContainer = Color(0xFF4A248C),
    error = UtilityColors.Error,
    onError = Color.White,
    errorContainer = UtilityColors.ErrorContainer,
    onErrorContainer = Color(0xFF7F1D1D),
    background = UtilityColors.Background,
    onBackground = UtilityColors.TextPrimary,
    surface = UtilityColors.Surface,
    onSurface = UtilityColors.TextPrimary,
    surfaceVariant = Color(0xFFF0ECE3),
    onSurfaceVariant = UtilityColors.TextSecondary,
    surfaceContainerLowest = UtilityColors.Surface,
    surfaceContainerLow = Color(0xFFF7F4ED),
    surfaceContainer = Color(0xFFF2EEE5),
    surfaceContainerHigh = Color(0xFFECE7DC),
    surfaceContainerHighest = Color(0xFFE5E0D5),
    outline = UtilityColors.Border,
    outlineVariant = Color(0xFFDCD7CC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE9E4D9),
    onPrimary = Color(0xFF24231F),
    primaryContainer = Color(0xFF3D3932),
    onPrimaryContainer = Color(0xFFF3F0E8),
    secondary = Color(0xFFC9C3B8),
    onSecondary = Color(0xFF2A2824),
    secondaryContainer = Color(0xFF35322D),
    onSecondaryContainer = Color(0xFFF3F0E8),
    tertiary = Color(0xFFD0BAFF),
    onTertiary = Color(0xFF46217F),
    tertiaryContainer = Color(0xFF5C3799),
    onTertiaryContainer = Color(0xFFF0EAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF181816),
    onBackground = Color(0xFFF3F0E8),
    surface = Color(0xFF201F1B),
    onSurface = Color(0xFFF3F0E8),
    surfaceVariant = Color(0xFF35322D),
    onSurfaceVariant = Color(0xFFCFC8BC),
    surfaceContainerLowest = Color(0xFF181816),
    surfaceContainerLow = Color(0xFF201F1B),
    surfaceContainer = Color(0xFF292722),
    surfaceContainerHigh = Color(0xFF33302A),
    surfaceContainerHighest = Color(0xFF3D3932),
    outline = Color(0xFF968F84),
    outlineVariant = Color(0xFF4B4841),
)

private val UtilityShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

private val UtilityTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
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
    val colorScheme = if (useDarkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalUtilityDarkTheme provides useDarkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UtilityTypography,
            shapes = UtilityShapes,
            content = content,
        )
    }
}
