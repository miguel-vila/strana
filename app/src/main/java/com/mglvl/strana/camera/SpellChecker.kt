package com.mglvl.strana.camera

import android.content.Context
import android.util.Log
import org.apache.lucene.analysis.hunspell.Dictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File

/**
 * A utility class for spell checking words using Hunspell dictionary.
 */
class SpellChecker private constructor(private val hunspell: Hunspell) {

    companion object {
        private var instance: SpellChecker? = null

        /**
         * Initialize and get the SpellChecker instance.
         * 
         * @param context Application context to access assets
         * @return SpellChecker instance
         */
        fun getInstance(context: Context): SpellChecker {
            if (instance == null) {
                instance = createSpellChecker(context)
            }
            return instance!!
        }

        private fun createSpellChecker(context: Context): SpellChecker {
            try {
                // Create temporary files for the dictionary and affix files
                val tempDirFile = File(context.cacheDir, "hunspell_temp")
                if (!tempDirFile.exists()) {
                    tempDirFile.mkdirs()
                }
                val tempDir: Directory = FSDirectory.open(tempDirFile.toPath())

                val dictionary = Dictionary(
                    tempDir, "hunspell_", 
                    context.assets.open("en_US.aff"),
                    context.assets.open("en_US.dic")
                )
                // Create Hunspell instance
                return SpellChecker(Hunspell(dictionary))
            } catch (e: Exception) {
                Log.e("SpellChecker", "Error initializing Hunspell", e)
                throw e
            }
        }
    }

    /**
     * Check if a word is spelled correctly.
     * 
     * @param word The word to check
     * @return true if the word is spelled correctly, false otherwise
     */
    fun isSpelledCorrectly(word: String): Boolean {
        return hunspell.spell(word)
    }

    /**
     * Get spelling suggestions for a misspelled word.
     * 
     * @param word The misspelled word
     * @return List of suggested corrections
     */
    fun getSuggestions(word: String): List<String> {
        if (word.length <= 1 || isSpelledCorrectly(word)) {
            return emptyList()
        }
        return hunspell.suggest(word).toList()
    }

    /**
     * Get the best suggestion for a misspelled word based on frequency.
     * 
     * @param word The misspelled word
     * @return The best suggestion or null if no suggestions are available
     */
    fun getBestSuggestion(word: String): String? {
        val suggestions = getSuggestions(word)
        return if (suggestions.isNotEmpty()) {
            suggestions.maxBy { w -> StrangeWordConfig.getFreq(w) ?: 0 }
        } else {
            null
        }
    }

    /**
     * Check spelling and get correction information for a word.
     * 
     * @param word The word to check
     * @return A SpellCheckResult containing spelling information
     */
    fun checkSpelling(word: String): SpellCheckResult {
        val isCorrect = isSpelledCorrectly(word)
        val suggestions = if (!isCorrect) getSuggestions(word) else emptyList()
        val bestSuggestion = if (suggestions.isNotEmpty()) {
            suggestions.maxBy { w -> StrangeWordConfig.getFreq(w) ?: 0 }
        } else {
            null
        }

        return SpellCheckResult(
            word = word,
            isCorrect = isCorrect,
            suggestions = suggestions,
            bestSuggestion = bestSuggestion
        )
    }
}

/**
 * Data class to hold the result of a spell check operation.
 */
data class SpellCheckResult(
    val word: String,
    val isCorrect: Boolean,
    val suggestions: List<String>,
    val bestSuggestion: String?
)
