package app.aapswear.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.Trend
import app.aapswear.model.TrendVisuals

/** Canonical Sugarlicious trend visual. All phone previews use the same vector and geometry. */
@Composable
internal fun SugarliciousTrendIndicator(
    trend: Trend,
    modifier: Modifier = Modifier,
    color: Color = SugarliciousColors.TextPrimary,
    arrowSize: Dp = 25.dp,
) {
    val spec = TrendVisuals.spec(trend) ?: return
    val width = if (spec.arrowCount == 2) arrowSize * 2.15f else arrowSize * 1.28f
    val height = arrowSize * 1.76f
    Box(modifier = modifier.size(width = width, height = height), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (spec.arrowCount == 2) 1.dp else 0.dp),
        ) {
            repeat(spec.arrowCount) {
                Image(
                    painter = painterResource(R.drawable.ic_trend_arrow),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(color),
                    modifier = Modifier.size(arrowSize).graphicsLayer(rotationZ = spec.rotationDegrees),
                )
            }
        }
    }
}
