package com.axelsarassamit.gx12

import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.Layout
import java.util.Locale

data class BikeNavigationState(val sign: Int, val meters: Double?, val nextRoad: String,
    val currentRoad: String, val arrival: String?, val speedLimit: Int?, val notice: String = "")

/** One compositor for the 480-pixel bike image and its diagnostic preview. */
object BikeNavigationRenderer {
    fun draw(canvas: Canvas, map: Bitmap, layout: BikeNavigationLayout, state: BikeNavigationState, night: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(map, null, Rect(layout.panelWidth, 0, layout.width, layout.mapHeight), p)
        p.color = if (night) 0xff101724.toInt() else 0xff202632.toInt()
        canvas.drawRect(0f, 0f, layout.panelWidth.toFloat(), (layout.height-layout.creditHeight).toFloat(), p)
        p.color=Color.WHITE; p.style=Paint.Style.FILL
        arrow(canvas, p, state.sign, layout.panelWidth / 2f, 38f)
        p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.DEFAULT_BOLD; p.textSize=32f
        val distance = state.meters?.let { if (it >= 1000) String.format(Locale.US,"%.1f km",it/1000)
            else "${(it.coerceAtLeast(0.0)/10).toInt()*10} m" } ?: "GPS"
        canvas.drawText(distance, layout.panelWidth/2f, 89f, p)
        val title = state.notice.ifBlank { state.nextRoad.ifBlank { "Continue straight" } }
        wrapped(canvas, title, 9f, 104f, layout.panelWidth-18, 19f, 3)
        if (!state.arrival.isNullOrBlank()) {
            p.color=0xff293957.toInt(); canvas.drawRect(0f,(layout.mapHeight).toFloat(),layout.panelWidth.toFloat(),(layout.height-layout.creditHeight).toFloat(),p)
            p.color=Color.WHITE; p.textSize=15f; p.textAlign=Paint.Align.CENTER
            canvas.drawText("Arrival ${state.arrival}",layout.panelWidth/2f,layout.mapHeight+18f,p)
        }
        // Exactly one speed sign belongs to this image. Unknown limits remain absent.
        state.speedLimit?.let { SpeedLimitIcon.draw(canvas,layout.speedX,layout.speedY,layout.speedRadius,it) }
        p.style=Paint.Style.FILL; p.color=0xe6101724.toInt()
        canvas.drawRect(layout.panelWidth.toFloat(),layout.mapHeight.toFloat(),480f,(layout.height-layout.creditHeight).toFloat(),p)
        p.color=Color.WHITE; p.textAlign=Paint.Align.LEFT; p.textSize=18f; p.typeface=Typeface.DEFAULT_BOLD
        val road=state.currentRoad.ifBlank { "RideDeck navigation" }
        val shown=TextUtils.ellipsize(road,TextPaint(p),(layout.mapWidth-20).toFloat(),TextUtils.TruncateAt.END).toString()
        canvas.drawText(shown,layout.panelWidth+10f,layout.mapHeight+19f,p)
        p.color=0xff101724.toInt(); canvas.drawRect(0f,(layout.height-layout.creditHeight).toFloat(),480f,layout.height.toFloat(),p)
        p.color=Color.WHITE; p.typeface=Typeface.DEFAULT; p.textSize=10f
        canvas.drawText("© MapTiler  © OpenStreetMap contributors",7f,layout.height-3f,p)
    }
    private fun wrapped(canvas: Canvas, text: String, x: Float, y: Float, width: Int, size: Float, lines: Int) {
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=size; typeface=Typeface.DEFAULT }
        val textLayout=StaticLayout.Builder.obtain(text,0,text.length,p,width).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
        canvas.save(); canvas.translate(x,y); textLayout.draw(canvas); canvas.restore()
    }
    private fun arrow(canvas: Canvas, p: Paint, sign: Int, x: Float, y: Float) {
        canvas.save(); canvas.translate(x,y)
        if (sign == 5) {
            p.style=Paint.Style.STROKE; p.strokeWidth=4f; canvas.drawLine(-18f,24f,-18f,-23f,p)
            p.style=Paint.Style.FILL
            for (row in 0..2) for (col in 0..3) if ((row+col)%2==0) canvas.drawRect(-15f+col*8,-23f+row*8,-7f+col*8,-15f+row*8,p)
        } else if (sign == 6 || sign == 7) {
            p.style=Paint.Style.STROKE; p.strokeWidth=7f; canvas.drawArc(-19f,-19f,19f,19f,30f,290f,false,p)
            p.style=Paint.Style.FILL
            canvas.drawPath(Path().apply { moveTo(20f,-13f); lineTo(7f,-12f); lineTo(16f,-1f); close() },p)
        } else {
            if(sign > 0) canvas.scale(-1f,1f)
            val path=Path()
            if(sign == 0) {
                path.moveTo(-5f,25f); path.lineTo(-5f,-9f); path.lineTo(-18f,-9f); path.lineTo(0f,-27f)
                path.lineTo(18f,-9f); path.lineTo(5f,-9f); path.lineTo(5f,25f)
            } else {
                if(kotlin.math.abs(sign)==1) canvas.rotate(28f)
                path.moveTo(10f,25f); path.lineTo(10f,-3f); path.quadTo(10f,-12f,0f,-12f)
                path.lineTo(-9f,-12f); path.lineTo(-9f,-25f); path.lineTo(-28f,-7f)
                path.lineTo(-9f,11f); path.lineTo(-9f,-2f); path.lineTo(0f,-2f); path.lineTo(0f,25f)
            }
            path.close(); canvas.drawPath(path,p)
        }
        p.style=Paint.Style.FILL; canvas.restore()
    }
}
