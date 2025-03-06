package com.mglvl.strana.camera

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.text.Text
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.Properties

/**
 * Utility class for processing text recognition results into Word objects
 */
class WordProcessor(private val context: Context) {
    
    private val spellChecker = SpellChecker.getInstance(context)
    private val props = Properties().apply {
        setProperty("annotators", "tokenize,pos")
    }
    private val pipeline = StanfordCoreNLP(props)
    
    /**
     * Process text recognition result into a list of Word objects
     * 
     * @param result The text recognition result from ML Kit
     * @return List of Word objects with bounds, spelling corrections, etc.
     */
    fun processTextRecognitionResult(result: Text): List<Word> {
        // Create a map to store word to bounding box mapping
        val wordBoundsMap = extractWordBounds(result)
        
        // Process the text with Stanford NLP
        val document = pipeline.processToCoreDocument(result.text)
        
        // Map tokens to Word objects
        return document.tokens().map { token ->
            // Create Word objects with bounds from the map
            val tokenWord = token.word()
            
            // Use the SpellChecker to check spelling and get suggestions
            val spellCheckResult = spellChecker.checkSpelling(tokenWord)
            
            Log.d(
                "WordMapping",
                "Token: '$tokenWord', bounds: ${wordBoundsMap[tokenWord]}, " +
                "isCorrect: ${spellCheckResult.isCorrect}, " +
                "suggestions: ${spellCheckResult.suggestions}"
            )
            
            Word(
                word = tokenWord,
                spellcheckedWord = spellCheckResult.bestSuggestion,
                posTag = token.tag(),
                bounds = wordBoundsMap[tokenWord],
                isSpelledCorrectly = spellCheckResult.isCorrect,
                suggestions = spellCheckResult.suggestions
            )
        }.filter { w: Word ->
            // Filter out proper nouns, punctuation, etc.
            !setOf(
                "NNP", "NNPS", ",", ".", "HYPH", "``", "''", ":", "RRB-", "LRB-"
            ).contains(w.posTag)
        }.filter { w: Word ->
            // Filter out short words and numbers
            w.word.length > 2 && !w.word.contains(Regex("[0-9]"))
        }
    }
    
    /**
     * Extract word bounds from text recognition result
     * 
     * @param result The text recognition result from ML Kit
     * @return Map of words to their bounding rectangles
     */
    private fun extractWordBounds(result: Text): Map<String, Rect> {
        val wordBoundsMap = mutableMapOf<String, Rect>()
        
        // Extract text blocks, lines, and elements with their bounding boxes
        for (block in result.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    element.boundingBox?.let { bounds ->
                        Log.d(
                            "TextRecognition",
                            "Found element: '${element.text}' with bounds: $bounds"
                        )
                        wordBoundsMap[element.text] = bounds
                    }
                }
            }
        }
        
        return wordBoundsMap
    }
    
    /**
     * Log summary of processed words
     * 
     * @param words List of processed Word objects
     */
    fun logWordsSummary(words: List<Word>) {
        if (words.isNotEmpty()) {
            // Log summary of words with bounds
            val wordsWithValidBounds = words.count { it.bounds != null }
            Log.d(
                "WordsWithBounds",
                "Total words: ${words.size}, Words with bounds: $wordsWithValidBounds"
            )
            
            words.forEach { w ->
                Log.d(
                    "WordProcessor",
                    "word: '${w.word}', bounds: ${w.bounds}"
                )
            }
        }
    }
}
