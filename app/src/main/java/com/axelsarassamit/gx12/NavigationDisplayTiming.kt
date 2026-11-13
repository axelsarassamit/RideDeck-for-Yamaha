package com.axelsarassamit.gx12

/** Monotonic render pacing; unknown speed is never treated as a proven standstill. */
object NavigationDisplayTiming {
    fun due(last:Long,now:Long,speedMps:Double?,changed:Boolean):Boolean {
        if(last<=0 || changed)return true
        val idle=speedMps!=null&&speedMps.isFinite()&&speedMps in 0.0..<0.5
        return now-last>=if(idle)2000 else 400
    }
    class Heading {
        private var last:Double?=null
        fun resolve(reliable:Double?,route:Double?):Double {
            reliable?.takeIf{it.isFinite()}?.let{last=((it%360)+360)%360;return last!!}
            route?.takeIf{it.isFinite()}?.let{last=((it%360)+360)%360;return last!!}
            return last?:0.0
        }
        fun clear(){last=null}
    }
}
