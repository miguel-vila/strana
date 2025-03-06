package com.mglvl.strana.camera

import android.content.Context
import android.util.Log

// Configuration for what makes a word "strange"
object StrangeWordConfig {
    private val wordsFreq = mutableMapOf<String, Int>()
    private val commonWords = mutableSetOf<String>()
    private const val TOP_WORDS_COUNT = 40_000
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            val inputStream = context.assets.open("en_50k.txt")
            val reader = inputStream.bufferedReader()

            var count = 0
            reader.useLines { lines ->
                lines.forEach { line ->
                    val segments = line.split(" ")
                    val word = segments.get(0).trim().lowercase()
                    val freq = segments.get(1).trim().toInt()
                    wordsFreq.set(word, freq)
                    if (count < TOP_WORDS_COUNT) {
                        commonWords.add(word)
                        count++
                    }
                }
            }

            isInitialized = true
            Log.d("StrangeWordConfig", "Loaded $count common words")

        } catch (e: Exception) {
            Log.e("StrangeWordConfig", "Error loading word list", e)
            throw e
        }
    }

    // Function to determine if a word is strange based on current config
    fun isStrange(word: String): Boolean {
        // Consider misspelled words as strange
        if (word.length > 3 && !commonWords.contains(word.lowercase())) {
            return true
        }
        return false
    }

    fun getFreq(word: String): Int? {
        return wordsFreq.get(word.lowercase())
    }
}
