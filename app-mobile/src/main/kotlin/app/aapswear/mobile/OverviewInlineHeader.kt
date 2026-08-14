package app.aapswear.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors

private val SettingsButtonBackground = Color(0xFF4A4A4A)

@Composable
internal fun OverviewInlineHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_monochrome_outlined),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(SugarliciousColors.TextPrimary),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "Sugarlicious",
            color = SugarliciousColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.size(38.dp).clickable(onClick = onSettings),
            shape = RoundedCornerShape(13.dp),
            color = SettingsButtonBackground,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Einstellungen",
                modifier = Modifier.padding(9.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}
