package com.axelsarassamit.gx12

import android.R
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Shields this activity's controls without pausing navigation, media or recording. */
object RainLock {
    private const val TAG = "ridedeck_rain_lock"
    private fun content(activity: Activity): FrameLayout = activity.findViewById(R.id.content)
    @JvmStatic fun active(activity: Activity) = content(activity).findViewWithTag<View>(TAG) != null

    @JvmStatic fun engage(activity: Activity) {
        val root=content(activity)
        if(active(activity))return
        fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
        val children=(0 until root.childCount).map { root.getChildAt(it) }.associateWith { it.importantForAccessibility }
        children.keys.forEach { it.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        val shield=FrameLayout(activity).apply { tag=TAG; isClickable=true;isFocusable=true }
        
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val unlock=Button(activity).apply {
            text="Rain Lock Active · Hold to Unlock"; isAllCaps=false
            RideStyle.action(this,true)
            setOnLongClickListener {
                root.removeView(shield)
                children.forEach { (view,value)->view.importantForAccessibility=value }
                true
            }
        }
        container.addView(unlock, LinearLayout.LayoutParams(-1, dp(58)).apply {
            leftMargin=dp(12);rightMargin=dp(12);topMargin=dp(12)
        })

        val weatherNotice = TextView(activity).apply {
            text = "Screen touch protection enabled for wet weather riding"
            textSize = 13f
            setTextColor(RideStyle.MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        container.addView(weatherNotice)

        shield.addView(container, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        
        ViewCompat.setOnApplyWindowInsetsListener(shield) { _, insets ->
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            shield.setPadding(safe.left,safe.top,safe.right,safe.bottom);insets
        }
        root.addView(shield,FrameLayout.LayoutParams(-1,-1))
        ViewCompat.requestApplyInsets(shield)
        shield.announceForAccessibility("Rain lock on. Screen touch protection enabled.")
    }
}
