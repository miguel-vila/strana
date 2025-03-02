package com.mglvl.strana.camera

import android.content.Context
import android.util.Log

// Configuration for what makes a word "strange"
object StrangeWordConfig {
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
                    if (count < TOP_WORDS_COUNT) {
                        val word = line.split(" ").get(0).trim().lowercase()
                        commonWords.add(word)
                        count++
                    } else {
                        return@forEach
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
        return !commonWords.contains(word.lowercase())
    }
}
