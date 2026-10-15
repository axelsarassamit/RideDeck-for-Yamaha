package com.axelsarassamit.gx12

/** Only speaks delay returned for the active route; nearby incident lists are not route evidence. */
class TrafficAnnouncements {
    data class Cue(val minutes:Int) {
        val text:String get()="TomTom reports about $minutes minutes of traffic delay on your route."
    }
    private var delayMillis=0L
    private var updatedAt:Long?=null
    private var baseline=0
    private var lastSpokenAt:Long?=null
    fun reset() { delayMillis=0; updatedAt=null; baseline=0; lastSpokenAt=null }
    fun report(delay:Long,now:Long) {
        delayMillis=delay.takeIf { it in 0..86400000 } ?: 0
        updatedAt=now
        baseline=minOf(baseline,minutes())
    }
    private fun minutes()=((delayMillis+30000)/60000).toInt()
    fun next(now:Long):Cue? {
        val age=updatedAt?.let{now-it} ?: return null
        if(age !in 0..600000 || delayMillis<120000 || minutes()<baseline+2) return null
        if(lastSpokenAt?.let{now-it<180000}==true) return null
        return Cue(minutes())
    }
    fun accepted(cue:Cue,now:Long) { baseline=cue.minutes; lastSpokenAt=now }
}
