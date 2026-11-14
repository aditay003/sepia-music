package app.sepia.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.sepia.music.Graph
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6F4A37),
    onPrimary = Color(0xFFFFF6EE),
    primaryContainer = Color(0xFFE9D8C8),
    onPrimaryContainer = Color(0xFF2B1A12),
    secondary = Color(0xFF8A6F5F),
    onSecondary = Color.White,
    background = Color(0xFFF5EADF),
    onBackground = Color(0xFF2B1A12),
    surface = Color(0xFFF5EADF),
    onSurface = Color(0xFF2B1A12),
    surfaceVariant = Color(0xFFEBDDD0),
    onSurfaceVariant = Color(0xFF8A6F5F),
    outline = Color(0xFFCDB8A8),
    outlineVariant = Color(0xFFE2D1C2),
    surfaceContainerLowest = Color(0xFFFBF4EC),
    surfaceContainerLow = Color(0xFFF1E4D8),
    surfaceContainer = Color(0xFFEBDDD0),
    surfaceContainerHigh = Color(0xFFE6D6C7),
    surfaceContainerHighest = Color(0xFFE0CFBF),
    error = Color(0xFFB3261E)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD9B08C),
    onPrimary = Color(0xFF2B1A12),
    primaryContainer = Color(0xFF4A3326),
    onPrimaryContainer = Color(0xFFF3E6DA),
    secondary = Color(0xFFB49B8C),
    onSecondary = Color(0xFF2B1A12),
    background = Color(0xFF1E1511),
    onBackground = Color(0xFFF3E6DA),
    surface = Color(0xFF1E1511),
    onSurface = Color(0xFFF3E6DA),
    surfaceVariant = Color(0xFF2E211A),
    onSurfaceVariant = Color(0xFFB49B8C),
    outline = Color(0xFF5A463B),
    outlineVariant = Color(0xFF3A2B22),
    surfaceContainerLowest = Color(0xFF191110),
    surfaceContainerLow = Color(0xFF261A15),
    surfaceContainer = Color(0xFF2B1E18),
    surfaceContainerHigh = Color(0xFF332419),
    surfaceContainerHighest = Color(0xFF3A2B22),
    error = Color(0xFFFFB4AB)
)

private val SepiaType = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp
    )
)

/** The user's choice from Settings: "system", "light" or "dark". Changing it re-themes the app at once. */
object AppTheme {
    var mode by mutableStateOf("system")
        private set

    /** "default", "apple", "wavy", "ytm" or "spotify" */
    var playerStyle by mutableStateOf("default")
        private set

    fun load() {
        mode = Graph.prefs.themeMode
        playerStyle = Graph.prefs.playerStyle
    }

    fun choosePlayerStyle(value: String) {
        playerStyle = value
        Graph.prefs.playerStyle = value
    }

    fun set(value: String) {
        mode = value
        Graph.prefs.themeMode = value
    }

    @Composable
    fun isDark(): Boolean = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun SepiaTheme(content: @Composable () -> Unit) {
    val scheme = if (AppTheme.isDark()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = SepiaType) {
        // Without this, any text or icon with no colour of its own is black - unreadable in dark mode.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
