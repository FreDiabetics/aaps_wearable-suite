package app.aapswear.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Sugarlicious brand and semantic color tokens.
 *
 * Keep these values in sync with res/values/colors.xml while the app uses
 * both classic Android Views and Jetpack Compose during the migration.
 */
object SugarliciousColors {
    // Brand
    val BrandGreen = Color(0xFF5FC479) // exact green used by the app logo
    val Primary = Color(0xFF6DE892)    // brighter interaction accent on dark surfaces
    val OnPrimary = Color(0xFF181818)
    val Secondary = Color(0xFF19D7E8)
    val OnSecondary = Color(0xFF181818)

    // Neutral surfaces
    val Background = Color(0xFF181818)
    val Surface = Color(0xFF242424)
    val SurfaceHigh = Color(0xFF303030)
    val SurfaceRaised = Color(0xFF363636)
    val SurfaceSelected = Color(0xFF3A3A3A)
    val Border = Color(0xFF404040)

    // Content
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFB5B5B5)

    // Therapy/data accents
    val Green = Color(0xFF54DF30)
    val Blue = Color(0xFF64BFFF)
    val Orange = Color(0xFFFF9D18)
    val Yellow = Color(0xFFF4DE00)
    val Purple = Color(0xFFD69AFF)
    val Red = Color(0xFFFF5C69)
}
