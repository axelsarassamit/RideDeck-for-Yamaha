package com.axelsarassamit.gx12

import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.Layout
import java.util.Locale

data class BikeNavigationState(val sign: Int, val meters: Double?, val nextRoad: String,
    val currentRoad: String, val arrival: String?, val speedLimit: Int?, val notice: String = "",val alert:TrafficAlert?=null,
    val lanes:app.pillion.core.LaneSection?=null,val fuelRange:String?=null,val elapsedMs:Long=0)

/** One compositor for the 480-pixel bike image and its diagnostic preview. */
object BikeNavigationRenderer {
    fun draw(canvas: Canvas, map: Bitmap, layout: BikeNavigationLayout, state: BikeNavigationState, night: Boolean,
        accent:Int=AppearancePalette.accent(4,night)) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        val text=AppearancePalette.text(night);val muted=AppearancePalette.muted(night)
        val panel=AppearancePalette.panel(night);val raised=AppearancePalette.raised(night)
        canvas.drawColor(AppearancePalette.background(night))
        // Snapshot dimensions already match the central viewport. Do not crop or cover its rider marker.
        canvas.drawBitmap(map,null,Rect(layout.mapLeft,0,layout.panelLeft,layout.mapHeight),p)
        p.color=panel;canvas.drawRect(layout.panelLeft.toFloat(),0f,480f,(layout.height-layout.creditHeight).toFloat(),p)
        p.color=accent;canvas.drawRect(layout.panelLeft.toFloat(),0f,layout.panelLeft+1f,(layout.height-layout.creditHeight).toFloat(),p)
        val middle=layout.panelLeft+layout.panelWidth/2f
        canvas.save();canvas.clipRect(layout.panelLeft+3,0,480,layout.mapHeight)
        p.color=text;arrow(canvas,p,state.sign,middle,37f)
        val distance=state.meters?.takeIf{it.isFinite()&&it>=0}?.let {
            if(it>=1000)String.format(Locale.US,"%.1f km",it/1000) else "${(it/10).toInt()*10} m"
        }?:"GPS"
        p.color=text;p.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD);p.textAlign=Paint.Align.CENTER
        p.textSize=34f
        while(p.textSize>22f&&p.measureText(distance)>layout.panelWidth-14)p.textSize-=1f
        canvas.drawText(distance,middle,88f,p)
        val title=state.notice.ifBlank{state.nextRoad.ifBlank{"Continue straight"}}
        wrapped(canvas,title,layout.panelLeft+9f,104f,layout.panelWidth-18,19f,3,text,layout.mapHeight-109)
        canvas.restore()
        if(layout.junctionVisible&&state.lanes?.usable()==true)junction(canvas,layout,state.lanes,state.meters,night,accent)
        state.speedLimit?.let{SpeedLimitIcon.draw(canvas,layout.speedX,layout.speedY,layout.speedRadius,it)}
        state.alert?.let{TrafficAlertRenderer.draw(canvas,it,layout.mapLeft+4f,4f,layout.mapWidth-8f)}
        p.color=panel;canvas.drawRect(0f,layout.mapHeight.toFloat(),layout.panelLeft.toFloat(),(layout.height-layout.creditHeight).toFloat(),p)
        p.color=text;p.textAlign=Paint.Align.LEFT;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=17f
        val road=state.currentRoad.ifBlank{"RideDeck navigation"}
        canvas.drawText(TextUtils.ellipsize(road,TextPaint(p),(layout.panelLeft-20).toFloat(),TextUtils.TruncateAt.END).toString(),10f,layout.mapHeight+19f,p)
        p.color=raised;canvas.drawRect(layout.panelLeft.toFloat(),layout.mapHeight.toFloat(),480f,(layout.height-layout.creditHeight).toFloat(),p)
        val footer=BikeDisplayPresentation.footer(state.arrival,state.fuelRange,state.elapsedMs)
        val footerText="${footer.label} ${footer.value}"
        p.color=text;p.textAlign=Paint.Align.CENTER;p.textSize=16f
        while(p.textSize>12f&&p.measureText(footerText)>layout.panelWidth-10)p.textSize-=1f
        canvas.drawText(TextUtils.ellipsize(footerText,TextPaint(p),(layout.panelWidth-10).toFloat(),TextUtils.TruncateAt.END).toString(),middle,layout.mapHeight+18f,p)
        p.color=AppearancePalette.background(night);canvas.drawRect(0f,(layout.height-layout.creditHeight).toFloat(),480f,layout.height.toFloat(),p)
        p.color=muted;p.textAlign=Paint.Align.LEFT;p.typeface=Typeface.DEFAULT;p.textSize=10f
        canvas.drawText("© MapTiler  © OpenStreetMap contributors",7f,layout.height-3f,p)
    }
    private fun junction(canvas:Canvas,layout:BikeNavigationLayout,section:app.pillion.core.LaneSection,meters:Double?,night:Boolean,accent:Int) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        // The firmware may overlay zoom controls in x=0..44. Keep all lane meaning to their right.
        val left=48f;val right=layout.junctionWidth-5f
        canvas.save();canvas.clipRect(0,0,layout.junctionWidth,layout.mapHeight)
        p.color=AppearancePalette.panel(night);canvas.drawRect(0f,0f,layout.junctionWidth.toFloat(),layout.mapHeight.toFloat(),p)
        p.color=accent;p.textSize=13f;p.typeface=Typeface.DEFAULT_BOLD;p.textAlign=Paint.Align.CENTER
        canvas.drawText("LANES",(left+right)/2,18f,p)
        p.color=AppearancePalette.text(night);p.textSize=15f
        canvas.drawText(meters?.let{"${(it/10).toInt()*10} m"}?:"",(left+right)/2,38f,p)
        val selected=section.recommendedIndices().toSet();val count=section.lanes.size
        val middle=(left+right)/2;val horizon=52f;val bottom=layout.mapHeight-28f
        fun edge(i:Int,f:Float)=middle+(i.toFloat()/count-.5f)*(55f+f*(right-left-55f))
        for(i in section.lanes.indices) {
            p.color=if(i in selected)accent else AppearancePalette.raised(night)
            canvas.drawPath(Path().apply{moveTo(edge(i,0f),horizon);lineTo(edge(i+1,0f),horizon);lineTo(edge(i+1,1f),bottom);lineTo(edge(i,1f),bottom);close()},p)
        }
        p.color=AppearancePalette.line(night);p.strokeWidth=1f
        for(i in 0..count)canvas.drawLine(edge(i,0f),horizon,edge(i,1f),bottom,p)
        for(i in section.lanes.indices) {
            val lane=section.lanes[i]
            val signs=lane.preferred.ifEmpty{lane.valid}.ifEmpty{lane.directions}.take(2)
            for((n,sign) in signs.withIndex()) {
                val x=(edge(i,.7f)+edge(i+1,.7f))/2+(n-(signs.size-1)/2f)*7f
                canvas.save();canvas.translate(x,horizon+(bottom-horizon)*.7f)
                val scale=(1.15f/count).coerceAtMost(.45f);canvas.scale(scale,scale)
                p.color=if(i in selected)AppearancePalette.onAccent(night) else AppearancePalette.muted(night)
                arrow(canvas,p,sign,0f,0f);canvas.restore()
            }
        }
        wrapped(canvas,section.spoken(),left,layout.mapHeight-23f,(right-left).toInt(),11f,2,AppearancePalette.text(night),22)
        p.color=accent;canvas.drawRect(layout.junctionWidth-1f,0f,layout.junctionWidth.toFloat(),layout.mapHeight.toFloat(),p)
        canvas.restore()
    }
    private fun wrapped(canvas:Canvas,text:String,x:Float,y:Float,width:Int,size:Float,lines:Int,color:Int,maxHeight:Int) {
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=Typeface.DEFAULT_BOLD}
        fun build()=StaticLayout.Builder.obtain(text,0,text.length,p,width).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
        var result=build()
        while(p.textSize>12f&&(result.height>maxHeight||result.getEllipsisCount(result.lineCount-1)>0)){p.textSize-=1f;result=build()}
        canvas.save();canvas.clipRect(x,y,x+width,y+maxHeight);canvas.translate(x,y);result.draw(canvas);canvas.restore()
    }
    internal fun arrow(canvas: Canvas, p: Paint, sign: Int, x: Float, y: Float) {
        canvas.save(); canvas.translate(x,y)
        if (sign == 5) {
            p.style=Paint.Style.STROKE; p.strokeWidth=4f; canvas.drawLine(-18f,24f,-18f,-23f,p)
            p.style=Paint.Style.FILL
            for (row in 0..2) for (col in 0..3) if ((row+col)%2==0) canvas.drawRect(-15f+col*8,-23f+row*8,-7f+col*8,-15f+row*8,p)
        } else if (kotlin.math.abs(sign) == 8) {
            if (sign > 0) canvas.scale(-1f, 1f)
            p.style = Paint.Style.STROKE; p.strokeWidth = 9f
            canvas.drawPath(Path().apply {
                moveTo(15f, 25f); lineTo(15f, -5f); cubicTo(15f, -28f, -15f, -28f, -15f, -5f); lineTo(-15f, 7f)
            }, p)
            p.style = Paint.Style.FILL
            canvas.drawPath(Path().apply { moveTo(-29f, 3f); lineTo(-15f, 22f); lineTo(-1f, 3f); close() }, p)
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
