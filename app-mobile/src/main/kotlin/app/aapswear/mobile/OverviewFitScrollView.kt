package app.aapswear.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import kotlin.math.min

/**
 * Keeps the fully populated overview fixed inside the available viewport.
 *
 * Normal dashboard states remain regular scrollable content. As soon as all
 * overview tiles/graphs are enabled, the dashboard is proportionally scaled
 * to the viewport and this ScrollView stops intercepting vertical gestures.
 * Horizontal gestures still reach the Compose overview (menu swipe navigation).
 */
class OverviewFitScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle,
) : ScrollView(context, attrs, defStyleAttr) {

    private val dashboardPreferences: SharedPreferences =
        context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        applyOverviewFit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post(::applyOverviewFit)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (fitRequested()) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (fitRequested()) return false
        return super.onTouchEvent(ev)
    }

    override fun fling(velocityY: Int) {
        if (fitRequested()) return
        super.fling(velocityY)
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, if (fitRequested()) 0 else y)
    }

    private fun fitRequested(): Boolean {
        val overviewVisible =
            rootView.findViewById<View?>(R.id.top_app_bar)?.visibility == View.GONE
        return overviewFitRequested(dashboardPreferences, overviewVisible)
    }

    private fun applyOverviewFit() {
        val child = getChildAt(0) ?: return
        val enabled = fitRequested()
        val fade = rootView.findViewById<View?>(R.id.scroll_fade)

        if (!enabled) {
            if (child.scaleX != 1f) child.scaleX = 1f
            if (child.scaleY != 1f) child.scaleY = 1f
            child.pivotX = child.width / 2f
            child.pivotY = 0f
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
            fade?.visibility = View.VISIBLE
            return
        }

        super.scrollTo(0, 0)
        overScrollMode = OVER_SCROLL_NEVER
        fade?.visibility = View.GONE

        val contentHeight = child.measuredHeight.takeIf { it > 0 } ?: return
        val viewportHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val scale = min(1f, viewportHeight.toFloat() / contentHeight.toFloat())

        child.pivotX = child.width / 2f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
    }
}

internal fun overviewFitRequested(
    preferences: SharedPreferences,
    overviewVisible: Boolean,
): Boolean =
    overviewVisible &&
        preferences.getBoolean("showDetails", true) &&
        preferences.getBoolean("showCgmGraph", true) &&
        preferences.getBoolean("showMetabolicGraph", false)
