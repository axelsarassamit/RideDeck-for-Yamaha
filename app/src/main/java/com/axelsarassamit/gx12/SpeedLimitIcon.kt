package com.axelsarassamit.gx12

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Original road-sign drawing shared by the phone and streamed bike map. */
class SpeedLimitIcon(context: Context): View(context) {
    private var limit: Int? = null
    fun setLimit(value: Int?) {
        limit=value
        visibility=if(value == null) GONE else VISIBLE
        contentDescription=value?.let { "Mapped speed limit $it kilometers per hour" }.orEmpty()
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        limit?.let { draw(canvas,width/2f,height/2f,minOf(width,height)/2f-2f,it) }
    }
    companion object {
        fun draw(canvas: Canvas, x: Float, y: Float, radius: Float, limit: Int) {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color=Color.WHITE; canvas.drawCircle(x,y,radius,paint)
            paint.color=0xffd3232e.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=radius*0.16f
            canvas.drawCircle(x,y,radius-paint.strokeWidth/2,paint)
            paint.style=Paint.Style.FILL; paint.color=Color.BLACK; paint.textAlign=Paint.Align.CENTER
            paint.typeface=Typeface.DEFAULT_BOLD; paint.textSize=radius*(if(limit>=100) 0.81f else 1.0f)
            canvas.drawText(limit.toString(),x,y-radius*0.13f-(paint.ascent()+paint.descent())/2,paint)
            paint.typeface=Typeface.DEFAULT; paint.textSize=radius*0.34f
            canvas.drawText("km/h",x,y+radius*0.53f,paint)
        }
    }
}
