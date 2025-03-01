package com.mglvl.strana.dictionary

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Data classes for parsing the Dictionary API response
 */
data class DictionaryEntry(
    val word: String,
    val phonetics: List<Phonetic>,
    val meanings: List<Meaning>,
    val license: License?,
    val sourceUrls: List<String>?
)

data class Phonetic(
    val text: String?,
    val audio: String?,
    val sourceUrl: String?,
    val license: License?
)

data class Meaning(
    val partOfSpeech: String,
    val definitions: List<Definition>,
    val synonyms: List<String>?,
    val antonyms: List<String>?
)

data class Definition(
    val definition: String,
    val synonyms: List<String>?,
    val antonyms: List<String>?,
    val example: String?
)

data class License(
    val name: String,
    val url: String
)

/**
 * Error response from the API when word is not found
 */
data class DictionaryApiError(
    val title: String,
    val message: String,
    val resolution: String
)

/**
 * Simple data class to hold the extracted definition information
 */
data class WordDefinition(
    val word: String,
    val partOfSpeech: String?,
    val definition: String?
)

/**
 * Retrofit interface for the Dictionary API
 */
interface DictionaryApiService {
    @GET("entries/en/{word}")
    fun getDefinition(@Path("word") word: String): Call<List<DictionaryEntry>>
}

/**
 * Client for the Dictionary API
 */
class DictionaryApiClient {
    private val baseUrl = "https://api.dictionaryapi.dev/api/v2/"
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(DictionaryApiService::class.java)

    /**
     * Get the definition for a word
     * @param word The word to look up
     * @param callback Callback to handle the result
     */
    fun getDefinition(word: String, callback: (WordDefinition?) -> Unit) {
        service.getDefinition(word).enqueue(object : Callback<List<DictionaryEntry>> {
            override fun onResponse(
                call: Call<List<DictionaryEntry>>,
                response: Response<List<DictionaryEntry>>
            ) {
                if (response.isSuccessful) {
                    val entries = response.body()
                    if (entries != null && entries.isNotEmpty()) {
                        val entry = entries[0]
                        val meaning = entry.meanings.firstOrNull()
                        val definition = meaning?.definitions?.firstOrNull()?.definition
                        
                        callback(
                            WordDefinition(
                                word = entry.word,
                                partOfSpeech = meaning?.partOfSpeech,
                                definition = definition
                            )
                        )
                    } else {
                        callback(null)
                    }
                } else {
                    // 404 or other error
                    callback(null)
                }
            }

            override fun onFailure(call: Call<List<DictionaryEntry>>, t: Throwable) {
                // Network error or other failure
                callback(null)
            }
        })
    }

    /**
     * Get the definition for a word (suspending function for use with coroutines)
     * @param word The word to look up
     * @return The word definition or null if not found
     */
//    suspend fun getDefinitionSuspend(word: String): WordDefinition? {
//        return try {
//            val response = retrofit2.kotlin.coroutines.await.await(service.getDefinition(word))
//            if (response.isNotEmpty()) {
//                val entry = response[0]
//                val meaning = entry.meanings.firstOrNull()
//                val definition = meaning?.definitions?.firstOrNull()?.definition
//
//                WordDefinition(
//                    word = entry.word,
//                    partOfSpeech = meaning?.partOfSpeech,
//                    definition = definition
//                )
//            } else {
//                null
//            }
//        } catch (e: Exception) {
//            // 404 or other error
//            null
//        }
//    }
}
