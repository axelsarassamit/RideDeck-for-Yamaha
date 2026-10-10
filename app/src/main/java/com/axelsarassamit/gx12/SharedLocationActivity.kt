package com.axelsarassamit.gx12

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Shared content is untrusted. Resolve only recognized Google Maps HTTPS links. */
class SharedLocationActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        BikeDiagnostics.record(this, "Shared destination received")
        val text = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() else null
        if (text.isNullOrBlank() || text.length > 12000) { fail("Share a place from Google Maps to RideDeck."); return }
        val progress = AlertDialog.Builder(this).setTitle("Shared destination").setMessage("Finding the place...").setNegativeButton("Cancel") { _, _ -> finish() }.create()
        progress.setCancelable(false); progress.show()
        worker.execute {
            val result = runCatching { resolve(text) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                result.onSuccess { BikeDiagnostics.record(this, "Shared destination resolved"); confirm(it) }.onFailure { BikeDiagnostics.record(this, "Shared destination resolution failed exception=${it.javaClass.simpleName}"); fail("This shared link could not be read. Share the place again, or enter its full address in RideDeck.") }
            }
        }
    }

    private fun resolve(text: String): String {
        val link = Regex("https://[^\\s<>]+").find(text)?.value?.trimEnd('.', ',', ')')
        if (link == null) {
            require(!text.contains("://"))
            return text.trim().take(500)
        }
        var uri = Uri.parse(link)
        require(app.pillion.core.SharedMapLink.allowed(uri.toString()))
        repeat(6) {
            app.pillion.core.SharedMapLink.destination(uri.toString())?.let { return it }
            val connection = URL(uri.toString()).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000; connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "RideDeck-Android")
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = Uri.parse(URL(URL(uri.toString()), connection.getHeaderField("Location") ?: error("Missing redirect")).toString())
                    require(app.pillion.core.SharedMapLink.allowed(next.toString())); uri = next
                } else {
                    require(code == 200)
                    val html = connection.inputStream.use { input ->
                        val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                        while(output.size()<262144) {
                            val count=input.read(buffer,0,minOf(buffer.size,262144-output.size()))
                            if(count<0) break
                            output.write(buffer,0,count)
                        }
                        String(output.toByteArray(),Charsets.UTF_8)
                    }
                    // Some short links finish on an HTML page with a canonical Maps URL.
                    for (tag in Regex("<(?:meta|link)\\b[^>]+>",RegexOption.IGNORE_CASE).findAll(html)) {
                        if(!Regex("(?:og:url|canonical)",RegexOption.IGNORE_CASE).containsMatchIn(tag.value)) continue
                        val raw=Regex("(?:content|href)=[\"']([^\"']+)",RegexOption.IGNORE_CASE).find(tag.value)?.groupValues?.get(1) ?: continue
                        val candidate=android.text.Html.fromHtml(raw,0).toString()
                        if(app.pillion.core.SharedMapLink.allowed(candidate)) app.pillion.core.SharedMapLink.destination(candidate)?.let { return it }
                    }
                    // Name/address supplied by Maps is still confirmed in search, never started automatically.
                    val label=text.replace(link,"").trim().take(500)
                    require(label.length>=3 && !label.contains("://"))
                    return label
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }

    private fun confirm(destination: String) {
        val field = EditText(this).apply { setText(destination); textSize=24f; minHeight=(80*resources.displayMetrics.density).toInt(); hint = "Confirm the place or full address" }
        val dialog = AlertDialog.Builder(this).setTitle("Shared destination").setMessage("Check the destination while parked.")
            .setView(field).setNegativeButton("Cancel") { _, _ -> finish() }
            .setPositiveButton("Choose route", null).create()
        dialog.setOnCancelListener { finish() }
        dialog.setOnShowListener {
            listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE).forEach {
                dialog.getButton(it).apply { textSize=20f; minHeight=(80*resources.displayMetrics.density).toInt() }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val target = field.text.toString().trim()
                if (target.isEmpty()) { field.error = "Enter a destination"; return@setOnClickListener }
                if (BuildConfig.YAMAHA) {
                    val coordinate=runCatching { NavigationApi.coordinate(target) }.getOrNull()
                    if(coordinate!=null) RoutePreviewActivity.open(this,coordinate)
                    else startActivity(Intent(this, MainActivity::class.java).putExtra("shared_destination", target))
                    finish()
                    return@setOnClickListener
                }
                val coordinate = Regex("^-?\\d+(?:\\.\\d+)?,\\s*-?\\d+(?:\\.\\d+)?$").matches(target)
                val destinationUri = if (RidePreferences.selectedMap(this) == "com.waze" && coordinate) Uri.parse("https://waze.com/ul").buildUpon().appendQueryParameter("ll", target).appendQueryParameter("navigate", "yes").build()
                    else Uri.parse("geo:0,0").buildUpon().appendQueryParameter("q", target).build()
                try { startActivity(Intent(Intent.ACTION_VIEW, destinationUri).setPackage(RidePreferences.selectedMap(this))); finish() }
                catch (_: Exception) { field.error = "The selected map app cannot open this destination." }
            }
        }
        dialog.show()
    }
    private fun fail(message: String) {
        AlertDialog.Builder(this).setTitle("Shared destination").setMessage(message).setPositiveButton("Close") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
