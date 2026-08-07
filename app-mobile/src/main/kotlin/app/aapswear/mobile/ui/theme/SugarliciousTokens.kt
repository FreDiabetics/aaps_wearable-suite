@file:Suppress("unused")

package app.aapswear.mobile.ui.theme

import androidx.compose.ui.unit.dp

/** Shared layout tokens for the incremental Compose migration. */
// Some tokens are intentionally defined before their first screen-level use so the
// legacy View and Compose implementations can converge on one stable design scale.
object SugarliciousSpacing {
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 40.dp
}

object SugarliciousIconSize {
    val Small = 18.dp
    val Default = 24.dp
    val Navigation = 27.dp
    val Large = 32.dp
    val Hero = 48.dp
}

object SugarliciousComponentSize {
    val TouchTarget = 48.dp
    val TopBar = 64.dp
    val BottomNavigation = 72.dp
    val ChipMinHeight = 36.dp
}

object SugarliciousRadius {
    val Small = 8.dp
    val Medium = 12.dp
    val Card = 22.dp
    val Navigation = 28.dp
    val Dialog = 26.dp
    val Pill = 999.dp
}
