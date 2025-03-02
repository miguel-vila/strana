package com.mglvl.strana.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "saved_words")
data class SavedWord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val word: String,
    val definition: String = "",
    val savedDate: Date = Date()
)
