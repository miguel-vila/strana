package com.mglvl.strana.camera

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A composable that draws bounding boxes around words and detects taps on those boxes.
 * 
 * @param modifier The modifier to be applied to the Canvas
 * @param words List of words with their bounding boxes to be highlighted
 * @param inputImageWidth Original width of the captured image
 * @param inputImageHeight Original height of the captured image
 * @param savedWordsStatus Map of words to their saved status
 * @param onWordSelected Callback when a word is tapped
 */
@Composable
fun WordBoundingBoxOverlay(
    modifier: Modifier = Modifier,
    words: List<Word>,
    inputImageWidth: Float,
    inputImageHeight: Float,
    savedWordsStatus: Map<String, Boolean> = emptyMap(),
    onWordSelected: (Word) -> Unit
) {
    // Only show bounding boxes if we have recognized words
    if (words.isEmpty()) return
    
    // Draw bounding boxes overlay with touch detection
    Canvas(
        modifier = modifier
            .pointerInput(words) {
                detectTapGestures { tapOffset ->
                    // Calculate scale factors for touch coordinates
                    val widthScalingFactor = size.width / inputImageWidth
                    val heightScalingFactor = size.height / inputImageHeight

                    // Check if the tap is inside any word bounding box
                    val strangeWords = words.filter { StrangeWordConfig.isStrange(it.word) }

                    var wordSelected = false
                    for (word in strangeWords) {
                        if (wordSelected) break
                        word.bounds?.let { rect ->
                            val left = rect.left.toFloat() * widthScalingFactor
                            val top = rect.top.toFloat() * heightScalingFactor
                            val width = (rect.right - rect.left).toFloat() * widthScalingFactor
                            val height = (rect.bottom - rect.top).toFloat() * heightScalingFactor

                            // Create a Compose Rect for easier hit testing
                            val wordRect = Rect(left, top, left + width, top + height)

                            if (wordRect.contains(tapOffset)) {
                                onWordSelected(word)
                                wordSelected = true

                                // If the word has a spellchecked version, log it
                                if (word.spellcheckedWord != null) {
                                    Log.d(
                                        "WordSelection",
                                        "Selected word '${word.word}' has spellchecked version '${word.spellcheckedWord}'"
                                    )
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Calculate scale factors for bounding boxes
        val widthScalingFactor = size.width / inputImageWidth
        val heightScalingFactor = size.height / inputImageHeight

        Log.d(
            "Canvas",
            "Drawing canvas overlay. Canvas size: ${size.width}x${size.height}, Scale: $widthScalingFactor, $heightScalingFactor"
        )

        // Draw rectangles around strange words
        val strangeWords = words.filter { StrangeWordConfig.isStrange(it.word) }

        strangeWords.forEach { word ->
            word.bounds?.let { rect ->
                val left = rect.left.toFloat() * widthScalingFactor
                val top = rect.top.toFloat() * heightScalingFactor
                val width = (rect.right - rect.left).toFloat() * widthScalingFactor
                val height = (rect.bottom - rect.top).toFloat() * heightScalingFactor

                // Determine color based on saved status and spelling
                val borderColor = when {
                    savedWordsStatus[word.word] == true -> Color.Green  // Green for saved words
                    !word.isSpelledCorrectly -> Color.Yellow  // Yellow for misspelled words
                    else -> Color.Red  // Red for unsaved words
                }

                // Draw rectangle around the word
                drawRect(
                    color = borderColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 5f) // Increased width for better visibility
                )
            }
        }
    }
}
