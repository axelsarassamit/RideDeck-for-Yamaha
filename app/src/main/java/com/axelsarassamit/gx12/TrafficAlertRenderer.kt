package com.axelsarassamit.gx12

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils

/** Vector icons remain sharp on the 480-pixel bike screen and the phone map. */
object TrafficAlertRenderer {
    fun draw(canvas:Canvas,alert:TrafficAlert,x:Float,y:Float,width:Float) {
        val closed=alert.kind==IncidentKind.ROAD_CLOSED
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=if(closed)0xffa9222c.toInt() else 0xfff7bd37.toInt()
        canvas.drawRoundRect(x,y,x+width,y+48,5f,5f,p)
        p.color=if(closed)Color.WHITE else 0xff161c25.toInt()
        canvas.save();canvas.translate(x+23,y+24)
        if(closed){
            p.style=Paint.Style.STROKE;p.strokeWidth=3f;canvas.drawCircle(0f,0f,14f,p)
            p.style=Paint.Style.FILL;canvas.drawRoundRect(-10f,-3f,10f,3f,1f,1f,p)
        }else{
            p.style=Paint.Style.STROKE;p.strokeWidth=3f
            canvas.drawPath(Path().apply{moveTo(0f,-16f);lineTo(17f,13f);lineTo(-17f,13f);close()},p)
            p.style=Paint.Style.FILL;canvas.drawRoundRect(-2f,-7f,2f,3f,1f,1f,p);canvas.drawCircle(0f,8f,2f,p)
        }
        canvas.restore();p.typeface=Typeface.DEFAULT_BOLD;p.textSize=17f
        val title="Reported ${if(closed) "road closure" else "accident"}"
        canvas.drawText(TextUtils.ellipsize(title,TextPaint(p),width-54,TextUtils.TruncateAt.END).toString(),x+47,y+20,p)
        p.typeface=Typeface.DEFAULT;p.textSize=14f
        canvas.drawText("Near route · ${alert.distance}",x+47,y+39,p)
    }
}
