package com.axelsarassamit.gx12

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Original top-view artwork, shared by the preview and both map renderers. */
object YamahaPositionMarker {
    private val models = arrayOf("XMAX", "NMAX", "MT-07", "MT-09", "XSR900", "XSR900 GP", "R9", "TRACER 7")
    private val colours = arrayOf("Yamaha blue", "Black", "Red", "Grey", "White", "Green", "Yellow", "Purple")
    private val paints = intArrayOf(0xff216bff.toInt(), 0xff242833.toInt(), 0xffed4545.toInt(), 0xff9aa4b1.toInt(), 0xfff4f6fa.toInt(), 0xff39c97e.toInt(), 0xffffcf40.toInt(), 0xffad73ff.toInt())
    private fun model(c: Context) = RidePreferences.prefs(c).getInt("position_bike_icon", 0).coerceIn(models.indices)
    private fun colour(c: Context) = RidePreferences.prefs(c).getInt("position_bike_colour", 0).coerceIn(colours.indices)
    @JvmStatic fun description(c: Context) = "${models[model(c)]} / ${colours[colour(c)]}"

    fun draw(c: Context, canvas: Canvas, x: Float, y: Float, rotation: Float) =
        drawIcon(canvas, x, y, model(c), paints[colour(c)], rotation, 1f)

    private fun drawIcon(canvas: Canvas, x: Float, y: Float, model: Int, colour: Int, rotation: Float, scale: Float) {
        canvas.save(); canvas.translate(x,y); canvas.rotate(rotation); canvas.scale(scale,scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin=Paint.Join.ROUND; strokeCap=Paint.Cap.ROUND }
        // A bright outline keeps every colour visible over streets and the route line.
        p.color=0xbfffffff.toInt(); canvas.drawOval(-19f,-28f,19f,28f,p)
        p.color=0xff111820.toInt(); canvas.drawRoundRect(-4f,-25f,4f,-12f,3f,3f,p)
        canvas.drawRoundRect(-4f,13f,4f,26f,3f,3f,p)
        val body = Path()
        when(model) {
            0,1 -> { // Scooter fairing and footboards, with a wider XMAX nose.
                val width=if(model==0) 13f else 10f
                body.moveTo(0f,-21f); body.cubicTo(-width,-20f,-width,-8f,-9f,-2f)
                body.lineTo(-9f,14f); body.quadTo(0f,23f,9f,14f); body.lineTo(9f,-2f)
                body.cubicTo(width,-8f,width,-20f,0f,-21f); body.close()
            }
            5,6 -> { // GP and R9 fairings, with distinct rounded and pointed noses.
                body.moveTo(0f,if(model==6) -24f else -21f)
                body.quadTo(-12f,-19f,-14f,-3f); body.lineTo(-7f,5f); body.lineTo(-5f,17f)
                body.lineTo(0f,22f); body.lineTo(5f,17f); body.lineTo(7f,5f); body.lineTo(14f,-3f)
                body.quadTo(12f,-19f,0f,if(model==6) -24f else -21f); body.close()
            }
            7 -> { // Touring fairing, narrow waist and rear luggage rack.
                body.moveTo(0f,-22f); body.lineTo(-12f,-13f); body.lineTo(-10f,0f)
                body.lineTo(-6f,5f); body.lineTo(-8f,19f); body.lineTo(8f,19f)
                body.lineTo(6f,5f); body.lineTo(10f,0f); body.lineTo(12f,-13f); body.close()
            }
            else -> { // Naked/retro tanks; MT-09 has broader angular shoulders.
                val w=if(model==3) 11f else 8f
                body.moveTo(0f,-16f); body.lineTo(-w,-9f); body.lineTo(-w,0f)
                body.lineTo(-5f,7f); body.lineTo(-5f,18f); body.lineTo(5f,18f)
                body.lineTo(5f,7f); body.lineTo(w,0f); body.lineTo(w,-9f); body.close()
            }
        }
        p.style=Paint.Style.STROKE; p.strokeWidth=4f; p.color=Color.WHITE; canvas.drawPath(body,p)
        p.strokeWidth=1.5f; p.color=0xff101820.toInt(); canvas.drawPath(body,p)
        p.style=Paint.Style.FILL; p.color=colour; canvas.drawPath(body,p)
        p.color=0xff151d28.toInt(); canvas.drawRoundRect(-4f,0f,4f,15f,3f,3f,p)
        if(model in listOf(0,1,5,6,7)) {
            p.color=0xff80bed5.toInt(); canvas.drawRoundRect(-6f,-17f,6f,-10f,3f,3f,p)
        } else {
            p.color=0xffffefbf.toInt()
            if(model==4) canvas.drawCircle(0f,-17f,4f,p)
            else canvas.drawRoundRect(-5f,-18f,5f,-14f,2f,2f,p)
        }
        p.color=0xff18212b.toInt(); p.style=Paint.Style.STROKE; p.strokeWidth=2f
        canvas.drawLine(-14f,-7f,-5f,-9f,p); canvas.drawLine(5f,-9f,14f,-7f,p)
        p.style=Paint.Style.FILL; canvas.drawOval(-17f,-10f,-12f,-6f,p); canvas.drawOval(12f,-10f,17f,-6f,p)
        if(model==7) { p.color=0xff151d28.toInt(); canvas.drawRoundRect(-7f,16f,7f,21f,2f,2f,p) }
        // Forward indicator is outside the body, so front and rear stay clear at dash size.
        val arrow=Path().apply { moveTo(0f,-33f); lineTo(-4f,-28f); lineTo(4f,-28f); close() }
        p.style=Paint.Style.STROKE; p.strokeWidth=1.5f; p.color=0xff101820.toInt(); canvas.drawPath(arrow,p)
        p.style=Paint.Style.FILL; p.color=Color.WHITE; canvas.drawPath(arrow,p)
        canvas.restore()
    }

    @JvmStatic fun showPicker(activity: Activity) {
        var selectedModel=model(activity); var selectedColour=colour(activity)
        val density=activity.resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        val page=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(8)) }
        val preview=object: View(activity) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas); canvas.drawColor(0xff14251c.toInt())
                drawIcon(canvas,width/2f,height/2f,selectedModel,paints[selectedColour],0f,density*2f)
            }
        }
        page.addView(preview,LinearLayout.LayoutParams(-1,dp(144)))
        val modelButton=Button(activity).apply { isAllCaps=false; textSize=20f }
        val colourButton=Button(activity).apply { isAllCaps=false; textSize=20f }
        fun refresh() {
            modelButton.text="Model: ${models[selectedModel]}"; colourButton.text="Colour: ${colours[selectedColour]}"
            preview.contentDescription="${models[selectedModel]} in ${colours[selectedColour]}"; preview.invalidate()
        }
        modelButton.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("Yamaha bike icon").setSingleChoiceItems(models,selectedModel) { d,index -> selectedModel=index; d.dismiss(); refresh() }.setNegativeButton("Cancel",null).show()
        }
        colourButton.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("Bike colour").setSingleChoiceItems(colours,selectedColour) { d,index -> selectedColour=index; d.dismiss(); refresh() }.setNegativeButton("Cancel",null).show()
        }
        page.addView(modelButton); page.addView(colourButton)
        page.addView(TextView(activity).apply { text="For compatible StreetCross displays. Choose the look of your position marker."; textSize=16f; setPadding(0,dp(8),0,0) })
        refresh()
        AlertDialog.Builder(activity).setTitle("Bike position icon").setView(page).setNegativeButton("Cancel",null)
            .setPositiveButton("Save") { _,_ ->
                RidePreferences.prefs(activity).edit().putInt("position_bike_icon",selectedModel).putInt("position_bike_colour",selectedColour).apply()
                BikeDiagnostics.record(activity,"Position icon saved model=${models[selectedModel]} colour=${colours[selectedColour]}")
            }.show()
    }
}
