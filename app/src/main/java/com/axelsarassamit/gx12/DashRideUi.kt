package com.axelsarassamit.gx12

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.*
import java.util.Locale

object DashRideUi {
    @JvmStatic fun show(activity: Activity) {
        val main=Handler(Looper.getMainLooper())
        fun dp(n: Int)=(n*activity.resources.displayMetrics.density).toInt()
        val dialog=Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16));setBackgroundColor(0xff101722.toInt()) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { view,insets ->
            val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left+dp(16),bars.top+dp(16),bars.right+dp(16),bars.bottom+dp(16));insets
        }
        fun text(size: Float)=TextView(activity).apply { textSize=size;setTextColor(Color.WHITE);setPadding(0,0,0,dp(12)) }
        page.addView(text(28f).apply { text="RideDeck Dash" })
        val state=text(24f);page.addView(state)
        val detail=text(18f);page.addView(detail)
        page.addView(text(18f).apply { text="Start a ride here. Open Dash and start recording there. Pause or End stops background recording eligibility." })
        fun button(label: String,primary: Boolean=false)=Button(activity).apply {
            text=label;isAllCaps=false;textSize=22f;minHeight=dp(80);setTextColor(Color.WHITE)
            background=GradientDrawable().apply { setColor(if(primary)0xff087eff.toInt() else 0xff242f40.toInt());cornerRadius=dp(12).toFloat() }
            setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        val toggle=button("Start ride",true)
        val end=button("End ride")
        val row=LinearLayout(activity)
        row.addView(toggle,LinearLayout.LayoutParams(0,dp(80),1f).apply { rightMargin=dp(8) })
        row.addView(end,LinearLayout.LayoutParams(0,dp(80),1f))
        page.addView(row,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        val open=button("Open Dash").apply { setOnClickListener { DashCompanion.open(activity) } }
        page.addView(open,LinearLayout.LayoutParams(-1,dp(80)).apply { topMargin=dp(12) })
        page.addView(button("Close").apply { setOnClickListener { dialog.dismiss() } },LinearLayout.LayoutParams(-1,dp(80)).apply { topMargin=dp(12) })
        val update=object: Runnable {
            override fun run() {
                if(!dialog.isShowing) return
                val active=DashRideService.active();val elapsed=DashRideService.elapsed();val seconds=elapsed/1000
                val time=String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)
                state.text=if(active)"Ride active · $time" else if(elapsed>0)"Ride paused · $time" else "Ready to ride"
                toggle.text=if(active)"Pause ride" else if(elapsed>0)"Resume ride" else "Start ride"
                end.isEnabled=active || elapsed>0
                detail.text=if(!DashCompanion.installed(activity))"RideDeck Dash is not installed. Your ride timer can still run." else DashRideService.bridgeStatus
                main.postDelayed(this,500)
            }
        }
        toggle.setOnClickListener { if(DashRideService.active()) DashRideService.pause(activity) else DashRideService.start(activity);main.removeCallbacks(update);main.postDelayed(update,250) }
        end.setOnClickListener { DashRideService.end(activity);main.removeCallbacks(update);main.postDelayed(update,250) }
        dialog.setContentView(ScrollView(activity).apply { isFillViewport=true;setBackgroundColor(0xff101722.toInt());addView(page) })
        dialog.setOnDismissListener { main.removeCallbacksAndMessages(null) }
        dialog.show();dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xff101722.toInt()))
            setLayout(-1,-1);addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ScreenChrome.apply(this,true)
        };androidx.core.view.ViewCompat.requestApplyInsets(page);main.post(update)
    }
}
