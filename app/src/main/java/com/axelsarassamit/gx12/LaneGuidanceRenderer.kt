package com.axelsarassamit.gx12

import android.graphics.*
import app.pillion.core.LaneSection

/** Provider lane diagram, left-to-right; highlighted lanes are recommendations, not sensed occupancy. */
object LaneGuidanceRenderer {
    fun draw(canvas:Canvas,section:LaneSection,x:Float,y:Float,width:Float) {
        if(!section.usable())return
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=0xf0101724.toInt();canvas.drawRoundRect(x,y,x+width,y+48,6f,6f,p)
        val cell=width/section.lanes.size
        for((i,lane) in section.lanes.withIndex()) {
            val recommended=lane.preferred.isNotEmpty();val valid=lane.valid.isNotEmpty()
            p.color=when{recommended->0xff1675cb.toInt();valid->0xff555b40.toInt();else->0xff232d3d.toInt()}
            canvas.drawRoundRect(x+i*cell+2,y+3,x+(i+1)*cell-2,y+45,4f,4f,p)
            val signs=lane.preferred.ifEmpty{lane.valid}.ifEmpty{lane.directions}.take(2)
            for((n,sign) in signs.withIndex()) {
                canvas.save();canvas.translate(x+(i+.5f)*cell+(n-(signs.size-1)/2f)*12,y+23)
                canvas.scale(.42f,.42f);p.color=if(recommended||valid)Color.WHITE else 0xff8390a2.toInt()
                BikeNavigationRenderer.arrow(canvas,p,sign,0f,0f);canvas.restore()
            }
            if(recommended){p.color=Color.WHITE;canvas.drawRect(x+i*cell+cell*.25f,y+40,x+i*cell+cell*.75f,y+42,p)}
        }
    }
    fun fuel(canvas:Canvas,label:String?,height:Int) {
        if(label.isNullOrBlank())return
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=15f;typeface=Typeface.DEFAULT_BOLD}
        val width=(p.measureText(label)+16).coerceAtMost(264f)
        p.color=0xee101724.toInt();canvas.drawRoundRect(6f,height-30f,6+width,height-5f,5f,5f,p)
        p.color=Color.WHITE;canvas.drawText(label,14f,height-12f,p)
    }
}
