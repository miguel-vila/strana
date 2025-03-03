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
 * Data class to hold a single definition with its part of speech
 */
data class DefinitionItem(
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null
)

/**
 * Simple data class to hold the extracted definition information
 */
data class WordDefinition(
    val word: String,
    val definitions: List<DefinitionItem>? = null
) {
    // For backward compatibility
    val partOfSpeech: String?
        get() = definitions?.firstOrNull()?.partOfSpeech
    
    val definition: String?
        get() = definitions?.firstOrNull()?.definition
}

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
                        
                        // Extract all definitions from all meanings
                        val definitionItems = entry.meanings.flatMap { meaning ->
                            meaning.definitions.take(1).map { def ->
                                DefinitionItem(
                                    partOfSpeech = meaning.partOfSpeech,
                                    definition = def.definition,
                                    example = def.example
                                )
                            }
                        }
                        
                        callback(
                            WordDefinition(
                                word = entry.word,
                                definitions = definitionItems
                            )
                        )
                    } else {
                        callback(null)
                    }
                } else {
                    // 404 or other error - return WordDefinition with null definitions
                    callback(
                        WordDefinition(
                            word = word,
                            definitions = null
                        )
                    )
                }
            }

            override fun onFailure(call: Call<List<DictionaryEntry>>, t: Throwable) {
                // Network error or other failure - return WordDefinition with null definitions
                callback(
                    WordDefinition(
                        word = word,
                        definitions = null
                    )
                )
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
