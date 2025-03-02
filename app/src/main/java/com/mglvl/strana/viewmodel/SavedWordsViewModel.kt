package com.mglvl.strana.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mglvl.strana.data.AppDatabase
import com.mglvl.strana.data.SavedWord
import com.mglvl.strana.data.WordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SavedWordsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WordRepository
    val allWords: Flow<List<SavedWord>>
    
    init {
        val wordDao = AppDatabase.getDatabase(application).wordDao()
        repository = WordRepository(wordDao)
        allWords = repository.allWords
    }
    
    fun saveWord(word: String, definition: String = "") {
        viewModelScope.launch {
            repository.saveWord(SavedWord(word = word, definition = definition))
        }
    }
    
    fun deleteWord(word: SavedWord) {
        viewModelScope.launch {
            repository.deleteWord(word)
        }
    }
    
    suspend fun isWordSaved(word: String): Boolean {
        return repository.isWordSaved(word)
    }
}
