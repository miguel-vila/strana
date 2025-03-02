package com.mglvl.strana.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM saved_words ORDER BY savedDate DESC")
    fun getAllWords(): Flow<List<SavedWord>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: SavedWord)
    
    @Delete
    suspend fun deleteWord(word: SavedWord)
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_words WHERE word = :word LIMIT 1)")
    suspend fun isWordSaved(word: String): Boolean
}
