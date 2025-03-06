package com.mglvl.strana.datacollection

import android.graphics.Rect
import java.util.UUID

/**
 * Represents a word that has been collected during data collection
 */
data class CollectedWordData(
    val originalWord: String,
    val correctedWord: String?, // null if no correction needed
    val isMarkedStrange: Boolean,
    val bounds: Rect? = null,
    val posTag: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a session of collected words
 */
data class CollectionSession(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val words: List<CollectedWordData>,
    val fullText: String // The complete recognized text with corrections
)
