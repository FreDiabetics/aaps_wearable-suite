package app.aapswear.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Design-system entry point for all new Compose UI in the mobile app.
 *
 * The legacy View UI and Compose UI deliberately share the same palette and
 * dimensions during migration. Dynamic color stays disabled so Sugarlicious
 * keeps its own visual identity; it can be exposed as an option later.
 */
private val SugarliciousDarkColorScheme = darkColorScheme(
    primary = SugarliciousColors.Primary,
    onPrimary = SugarliciousColors.OnPrimary,
    primaryContainer = SugarliciousColors.SurfaceHigh,
    onPrimaryContainer = SugarliciousColors.TextPrimary,
    secondary = SugarliciousColors.Secondary,
    onSecondary = SugarliciousColors.OnSecondary,
    secondaryContainer = SugarliciousColors.SurfaceHigh,
    onSecondaryContainer = SugarliciousColors.TextPrimary,
    background = SugarliciousColors.Background,
    onBackground = SugarliciousColors.TextPrimary,
    surface = SugarliciousColors.Surface,
    onSurface = SugarliciousColors.TextPrimary,
    surfaceVariant = SugarliciousColors.SurfaceHigh,
    onSurfaceVariant = SugarliciousColors.TextSecondary,
    outline = SugarliciousColors.Border,
    error = SugarliciousColors.Red,
    onError = SugarliciousColors.OnPrimary,
)

@Composable
fun SugarliciousTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SugarliciousDarkColorScheme,
        typography = SugarliciousTypography,
        shapes = SugarliciousShapes,
        content = content,
    )
}
