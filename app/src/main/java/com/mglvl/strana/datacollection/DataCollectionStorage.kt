package com.mglvl.strana.datacollection

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import java.io.File

/**
 * Utility for storing and exporting collected word data
 */
object DataCollectionStorage {
    private const val DIRECTORY_NAME = "strana_data_collection"
    private const val FILE_PREFIX = "session_"
    private const val FILE_EXTENSION = ".json"
    private const val CSV_FILE_NAME = "strana_collected_data.csv"
    
    /**
     * Save a collection session to JSON file
     * 
     * @param context Application context
     * @param session The session to save
     * @return Uri to the saved file
     */
    fun saveCollectionSession(context: Context, session: CollectionSession): Uri {
        // Ensure directory exists
        val directory = File(context.getExternalFilesDir(null), DIRECTORY_NAME)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        // Create JSON file
        val filename = "$FILE_PREFIX${session.id}$FILE_EXTENSION"
        val file = File(directory, filename)
        
        // Convert session to JSON
        val json = Gson().toJson(session)
        file.writeText(json)
        
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * Get all saved sessions
     * 
     * @param context Application context
     * @return List of saved collection sessions
     */
    fun getSavedSessions(context: Context): List<CollectionSession> {
        val directory = File(context.getExternalFilesDir(null), DIRECTORY_NAME)
        if (!directory.exists()) return emptyList()
        
        return directory.listFiles { file -> 
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_EXTENSION)
        }?.mapNotNull { file ->
            try {
                val json = file.readText()
                Gson().fromJson(json, CollectionSession::class.java)
            } catch (e: Exception) {
                Log.e("DataCollection", "Failed to parse session file: ${file.name}", e)
                null
            }
        } ?: emptyList()
    }
    
    /**
     * Export all data to a CSV file that can be shared
     * 
     * @param context Application context
     * @return Uri to the exported CSV file
     */
    fun exportAllDataToCsv(context: Context): Uri? {
        val sessions = getSavedSessions(context)
        if (sessions.isEmpty()) return null
        
        val csvFile = File(context.cacheDir, CSV_FILE_NAME)
        if (csvFile.exists()) {
            csvFile.delete()
        }
        
        try {
            csvFile.bufferedWriter().use { writer ->
                // Write CSV header
                writer.write("SessionID,Timestamp,FullText,StrangeWords\n")
                
                // Write each session as a row
                sessions.forEach { session ->
                    val strangeWords = session.words
                        .filter { it.isMarkedStrange }
                        .map { it.correctedWord ?: it.originalWord }
                        .joinToString(";")
                    
                    // Escape fields for CSV
                    val escapedFullText = "\"${session.fullText.replace("\"", "\"\"")}\""
                    val escapedStrangeWords = "\"${strangeWords.replace("\"", "\"\"")}\""
                    
                    writer.write("${session.id},${session.timestamp},${escapedFullText},${escapedStrangeWords}\n")
                }
            }
            
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
        } catch (e: Exception) {
            Log.e("DataCollection", "Failed to create CSV file", e)
            return null
        }
    }
}
