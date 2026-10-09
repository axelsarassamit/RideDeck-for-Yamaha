package app.pillion.core

/** Validates private saved destination fields before a system service restart. */
data class NavigationResumeTarget(val label: String,val latitude: Double,val longitude: Double) {
    companion object {
        fun restore(enabled: Boolean,label: String?,latitude: String?,longitude: String?): NavigationResumeTarget? {
            if(!enabled) return null
            val lat=latitude?.toDoubleOrNull() ?: return null
            val lon=longitude?.toDoubleOrNull() ?: return null
            if(!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return NavigationResumeTarget(label?.take(300)?.ifBlank { "Destination" } ?: "Destination",lat,lon)
        }
    }
}
