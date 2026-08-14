package app.aapswear.mobile

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

/**
 * Keeps the fully populated overview fixed to the display while preserving normal scrolling on
 * Watch and Settings screens and on overview configurations that need it.
 *
 * The Compose overview already scales its cards and graphs to the available screen height when all
 * overview sections are enabled. In that state vertical scrolling would only move an intentionally
 * fixed dashboard, so this view leaves touch events to its children instead of intercepting them.
 */
class DashboardScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle,
) : ScrollView(context, attrs, defStyleAttr) {

    private fun overviewLockedToViewport(): Boolean {
        val preferences =
            context.getSharedPreferences(
                "dashboard_ui",
                Context.MODE_PRIVATE,
            )
        return preferences.getBoolean("showDetails", true) &&
            preferences.getBoolean("showCgmGraph", true) &&
            preferences.getBoolean("showMetabolicGraph", false)
    }

    private fun scrollingLocked(): Boolean =
        id == R.id.dashboard_scroll && overviewLockedToViewport() &&
            rootView.findViewById<android.view.View>(R.id.top_app_bar)?.visibility == android.view.View.GONE

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (scrollingLocked()) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && scrollY != 0) {
                scrollTo(0, 0)
            }
            return false
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scrollingLocked()) return false
        return super.onTouchEvent(event)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (scrollingLocked() && scrollY != 0) {
            scrollTo(0, 0)
        }
    }
}
