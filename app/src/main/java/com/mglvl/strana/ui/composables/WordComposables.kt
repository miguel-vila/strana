package com.mglvl.strana.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mglvl.strana.R
import com.mglvl.strana.camera.Word
import com.mglvl.strana.dictionary.WordDefinition

@Composable
fun WordsAndDefinitionsArea(
    modifier: Modifier = Modifier,
    selectedWord: Word?,
    selectedWordDefinition: WordDefinition?,
    isCameraActive: Boolean,
    hasStrangeWords: Boolean,
    isWordSaved: Boolean = false,
    onSaveWord: (String, String?) -> Unit = { _, _ -> }
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxSize()
        ) {
            // Header text with appropriate instruction based on state
            Text(
                text = when {
                    isCameraActive -> stringResource(R.string.camera_instruction)
                    selectedWord != null -> stringResource(R.string.word_details)
                    hasStrangeWords -> stringResource(R.string.tap_word_instruction)
                    else -> stringResource(R.string.no_strange_words)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Show content based on state
            when {
                // When camera is active, show camera guidance
                isCameraActive -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.camera_guidance),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // When a word is selected, show its definition
                selectedWord != null -> {
                    // Use the word to look up (either original or spellchecked)
                    val wordToLookup = selectedWord.spellcheckedWord ?: selectedWord.word

                    WordDefinitionCard(
                        word = wordToLookup,
                        definition = selectedWordDefinition,
                        isWordSaved = isWordSaved,
                        onSaveWord = { word, definition ->
                            onSaveWord(word, definition)
                        },
                        selectedWord = selectedWord
                    )
                }
                // When image is captured but no word is selected, show tap instruction
                hasStrangeWords -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tap_word_explanation),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // When no strange words are found
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_strange_words_guidance),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WordDefinitionCard(
    word: String,
    definition: WordDefinition?,
    isWordSaved: Boolean = false,
    onSaveWord: (String, String?) -> Unit = { _, _ -> },
    selectedWord: Word? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Word header with save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Display the word (or spellchecked version if available)
                    Text(
                        text = selectedWord?.spellcheckedWord ?: word,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // If this is a spellchecked word, show the original below it
                    if (selectedWord?.spellcheckedWord != null && selectedWord.spellcheckedWord != selectedWord.word) {
                        Text(
                            text = "(corrected from: ${selectedWord.word})",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorResource(id = R.color.red),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                // Save/bookmark button
                IconButton(
                    onClick = {
                        if (!isWordSaved) {
                            // Use the spellchecked word if available
                            val wordToSave = selectedWord?.spellcheckedWord ?: word
                            onSaveWord(wordToSave, definition?.definition)
                        }
                    },
                    enabled = !isWordSaved
                ) {
                    Icon(
                        imageVector = if (isWordSaved) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (isWordSaved) "Word saved" else "Save word",
                        tint = if (isWordSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (definition == null) {
                // Show loading indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (definition.definitions == null || definition.definitions.isEmpty()) {
                // Show error message in red when definition is not found (404)
                Text(
                    text = "Meaning couldn't be found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorResource(id = R.color.red)
                )
            } else {
                // Show all definitions grouped by part of speech
                definition.definitions.forEach { definitionItem ->
                    // Part of speech header
                    Text(
                        text = definitionItem.partOfSpeech,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Definition
                    Text(
                        text = definitionItem.definition,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Example if available
                    definitionItem.example?.let { example ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Example: \"$example\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
