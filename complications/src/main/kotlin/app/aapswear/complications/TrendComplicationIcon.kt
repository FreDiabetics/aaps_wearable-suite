package app.aapswear.complications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.MonochromaticImage
import app.aapswear.model.Trend
import app.aapswear.model.TrendVisuals
import kotlin.math.roundToInt

/** Renders the exact Sugarlicious trend vector used by the phone overview. */
internal object TrendComplicationIcon {
    fun monochromaticImage(
        context: Context,
        trend: Trend,
        sizePx: Int = 72,
    ): MonochromaticImage? {
        val bitmap = render(context, trend, sizePx) ?: return null
        return MonochromaticImage.Builder(Icon.createWithBitmap(bitmap)).build()
    }

    fun render(
        context: Context,
        trend: Trend,
        sizePx: Int,
    ): Bitmap? {
        val spec = TrendVisuals.spec(trend) ?: return null
        val drawable = context.getDrawable(R.drawable.ic_trend_arrow)?.mutate() ?: return null
        drawable.setTint(Color.WHITE)
        val unit = (sizePx * if (spec.arrowCount == 2) 0.72f else 0.86f).roundToInt().coerceAtLeast(1)
        val base = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(base)

        fun drawArrow(offset: Float) {
            canvas.save()
            canvas.rotate(spec.rotationDegrees, sizePx / 2f, sizePx / 2f)
            val left = ((sizePx - unit) / 2f + offset).roundToInt()
            val top = ((sizePx - unit) / 2f).roundToInt()
            drawable.setBounds(left, top, left + unit, top + unit)
            drawable.draw(canvas)
            canvas.restore()
        }

        if (spec.arrowCount == 2) {
            val gap = sizePx * 0.12f
            drawArrow(-gap)
            drawArrow(gap)
        } else {
            drawArrow(0f)
        }
        return base
    }
}
