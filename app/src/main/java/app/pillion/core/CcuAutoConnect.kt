package app.pillion.core

/** At most three data starts per authenticated map connection. Explicit Stop always wins. */
class CcuAutoConnect {
    var peer = ""; private set
    var attempts = 0; private set
    var paused = false; private set
    fun connected(address: String) { peer = address; attempts = 0; paused = false }
    fun disconnected() { peer = ""; paused = true }
    fun pause() { paused = true }
    fun attempt(address: String, enabled: Boolean, alreadyActive: Boolean): Boolean {
        if (!enabled || alreadyActive || paused || peer.isBlank() || !peer.equals(address, true) || attempts >= 3) return false
        attempts++
        return true
    }
}
