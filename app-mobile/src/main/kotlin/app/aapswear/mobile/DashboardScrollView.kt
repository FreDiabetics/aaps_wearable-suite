package app.aapswear.mobile

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

/** Keeps the one-screen overview fixed while retaining normal scrolling in long menus. */
class DashboardScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {
    var isUserScrollEnabled: Boolean = true

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        isUserScrollEnabled && super.onInterceptTouchEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        isUserScrollEnabled && super.onTouchEvent(event)
}
