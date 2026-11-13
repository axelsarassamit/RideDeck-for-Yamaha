package com.axelsarassamit.gx12

/** Coordinates are bike pixels, never phone dp. The CCU supplies its own clock/header. */
data class BikeNavigationLayout(val height: Int, val junctionVisible:Boolean = false) {
    init { require(height in listOf(234, 240)) }
    val width = 480
    val panelWidth = 132
    val creditHeight = 12
    val roadHeight = 26
    val panelLeft = width - panelWidth
    val junctionWidth = if(junctionVisible)168 else 0
    val mapLeft = junctionWidth
    val mapWidth = panelLeft - mapLeft
    val mapHeight = height - creditHeight - roadHeight
    val speedX = panelLeft - 31f
    val speedY = mapHeight - 31f
    val speedRadius = 27f

    companion object {
        /** Map frames own their sign. Native Turn-by-turn owns its sign when images stop. */
        fun nativeSpeedLimit(imagesRequested: Boolean, limit: Int?): Int? = if (imagesRequested) null else limit
    }
}
