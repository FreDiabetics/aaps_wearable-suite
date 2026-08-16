package app.aapswear.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors

private val SettingsIconGray = Color(0xFF4A4A4A)

@Composable
internal fun OverviewInlineHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).offset(y = (-2).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 31.dp, height = 38.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(46.dp).offset(x = (-10).dp),
            )
        }

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SugarliciousColors.TextPrimary)) {
                    append("Sugar")
                }
                withStyle(SpanStyle(color = SugarliciousColors.Primary)) {
                    append("licious")
                }
            },
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.weight(1f))

        SettingsHeaderButton(onSettings)
    }
}

@Composable
internal fun WatchMenuHeader(
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Zurück",
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(7.dp))

        Text(
            text = "Watch",
            color = SugarliciousColors.TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.weight(1f))
        SettingsHeaderButton(onSettings)
    }
}

@Composable
private fun SettingsHeaderButton(onSettings: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clickable(onClick = onSettings),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Einstellungen",
            modifier = Modifier.size(23.dp).offset(x = 7.dp),
            colorFilter = ColorFilter.tint(SettingsIconGray),
        )
    }
}
