package com.mglvl.strana.data

import kotlinx.coroutines.flow.Flow

class WordRepository(private val wordDao: WordDao) {
    val allWords: Flow<List<SavedWord>> = wordDao.getAllWords()
    
    suspend fun saveWord(word: SavedWord) {
        wordDao.insertWord(word)
    }
    
    suspend fun deleteWord(word: SavedWord) {
        wordDao.deleteWord(word)
    }
    
    suspend fun isWordSaved(word: String): Boolean {
        return wordDao.isWordSaved(word)
    }
}
