package app.aapswear.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compose design-system entry point for the mobile app.
 *
 * The first migration step intentionally mirrors the existing View palette so
 * Compose components can be introduced without changing the current look.
 */
private val SugarliciousDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6DE892),
    onPrimary = Color(0xFF181818),
    primaryContainer = Color(0xFF303030),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFF19D7E8),
    onSecondary = Color(0xFF181818),
    background = Color(0xFF181818),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF242424),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFB5B5B5),
    outline = Color(0xFF404040),
    error = Color(0xFFFF5C69),
    onError = Color(0xFF181818),
)

val SugarliciousShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val SugarliciousTypography = Typography()

@Composable
fun SugarliciousTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SugarliciousDarkColorScheme,
        typography = SugarliciousTypography,
        shapes = SugarliciousShapes,
        content = content,
    )
}
