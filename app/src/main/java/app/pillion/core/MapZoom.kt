package app.pillion.core

class MapZoom {
    var level = 16.0
        private set
    private var chosen = false
    fun change(inside: Boolean): Double {
        chosen = true
        level = (level + if (inside) 1.0 else -1.0).coerceIn(3.0, 20.0)
        return level
    }
    fun navigationStarted() { if (!chosen) level = 17.0 }
}
