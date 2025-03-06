package com.mglvl.strana.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mglvl.strana.R
import com.mglvl.strana.dictionary.DictionaryApiClient
import com.mglvl.strana.dictionary.WordDefinition
import com.mglvl.strana.viewmodel.SavedWordsViewModel
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.Properties
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    dictionaryApiClient: DictionaryApiClient,
    savedWordsViewModel: SavedWordsViewModel = viewModel()
) {
    // State for selected word and its definition
    var selectedWord by remember { mutableStateOf<Word?>(null) }
    var selectedWordDefinition by remember { mutableStateOf<WordDefinition?>(null) }

    // State to track if the selected word is saved
    var isWordSaved by remember { mutableStateOf(false) }

    // Coroutine scope for launching coroutines
    val coroutineScope = rememberCoroutineScope()

    // Snackbar host state for showing messages
    val snackbarHostState = remember { SnackbarHostState() }

    // Shared state for recognized words
    var recognizedWords by remember { mutableStateOf<List<Word>>(emptyList()) }

    // State to track if camera is active (no image captured yet)
    var isCameraActive by remember { mutableStateOf(true) }

    // Check if the selected word is saved
    LaunchedEffect(selectedWord) {
        if (selectedWord != null) {
            isWordSaved = savedWordsViewModel.isWordSaved(selectedWord!!.word)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Camera preview takes up the top 75%
            CameraPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.75f),
                onWordsRecognized = { words ->
                    recognizedWords = words
                },
                onWordSelected = { word ->
                    selectedWord = word
                    selectedWordDefinition = null // Reset definition when new word is selected

                    // Fetch definition for the selected word
                    dictionaryApiClient.getDefinition(
                        word.spellcheckedWord ?: word.word
                    ) { definition ->
                        selectedWordDefinition = definition
                    }

                    // Check if the word is saved
                    coroutineScope.launch {
                        isWordSaved = savedWordsViewModel.isWordSaved(word.word)
                    }
                },
                onCameraStateChanged = { isActive ->
                    isCameraActive = isActive
                    // Reset word selection when returning to camera
                    if (isActive) {
                        selectedWord = null
                        selectedWordDefinition = null
                    }
                },
                savedWordsViewModel = savedWordsViewModel
            )

            // Words and definitions area takes up the bottom 40%
            WordsAndDefinitionsArea(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f),
                selectedWord = selectedWord,
                selectedWordDefinition = selectedWordDefinition,
                isCameraActive = isCameraActive,
                hasStrangeWords = recognizedWords.any { StrangeWordConfig.isStrange(it.word) },
                isWordSaved = isWordSaved,
                onSaveWord = { word, definition ->
                    savedWordsViewModel.saveWord(word, definition ?: "")
                    isWordSaved = true
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Word saved!"
                        )
                    }
                }
            )
        }

        // Snackbar host at the bottom of the screen
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onWordsRecognized: (List<Word>) -> Unit,
    onWordSelected: (Word) -> Unit,
    onCameraStateChanged: (Boolean) -> Unit,
    savedWordsViewModel: SavedWordsViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Create a remembered ImageCapture instance that persists across recompositions
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Create the text recognizer
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Get the SpellChecker instance
    val spellChecker = remember { SpellChecker.getInstance(context) }

    // State to track if scanning is in progress
    var isScanning by remember { mutableStateOf(false) }

    // State to hold the captured image bitmap
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // State to indicate text recognition is in progress
    var isRecognizingText by remember { mutableStateOf(false) }

    // State to hold the recognized words with their bounding boxes
    var wordsWithBounds by remember { mutableStateOf<List<Word>>(emptyList()) }

    // State to hold saved words status
    var savedWordsStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    // State to track the scaling factor applied to the original image
    var inputImageWidth by remember { mutableStateOf(1f) }
    var inputImageHeight by remember { mutableStateOf(1f) }

    val props = Properties()
    props.setProperty("annotators", "tokenize,pos")
    val pipeline = StanfordCoreNLP(props)

    // Update camera state whenever capturedBitmap changes
    onCameraStateChanged(capturedBitmap == null)

    Box(modifier = modifier.fillMaxSize()) {
        // Show either the camera preview or the captured image
        if (capturedBitmap != null) {
            // Show the frozen image with bounding boxes
            Box(modifier = Modifier.fillMaxSize()) {
                // Show the frozen image
                Image(
                    bitmap = capturedBitmap!!.asImageBitmap(),
                    contentDescription = "Captured Image",
                    modifier = Modifier.fillMaxSize()
                )

                // Use the refactored WordBoundingBoxOverlay component
                WordBoundingBoxOverlay(
                    modifier = Modifier.fillMaxSize(),
                    words = wordsWithBounds,
                    inputImageWidth = inputImageWidth,
                    inputImageHeight = inputImageHeight,
                    savedWordsStatus = savedWordsStatus,
                    onWordSelected = onWordSelected
                )

                // Show loading indicator while text recognition is in progress
                if (isRecognizingText) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Position the button at the bottom right when showing captured image
                Button(
                    onClick = {
                        capturedBitmap = null
                        wordsWithBounds = emptyList()
                        isRecognizingText = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text(text = "Open Camera")
                }
            }
        } else {
            // Camera preview
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            // Set up the image capture use case
                            val newImageCapture = ImageCapture.Builder().build()
                            imageCapture = newImageCapture

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    newImageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("CameraActivity", "Use case binding failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                // Position the scan button at the bottom right of the screen
                Button(
                    onClick = {
                        if (!isScanning) {
                            // Take a picture
                            isScanning = true

                            val currentImageCapture = imageCapture
                            Log.d("CameraActivity", "imageCapture: ${currentImageCapture != null}")

                            if (currentImageCapture != null) {
                                currentImageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                            Log.d("CameraActivity", "Image captured successfully")

                                            // Convert the captured image to bitmap and store it immediately
                                            val scaledBitmap = imageProxy.toScaledBitmap()
                                            capturedBitmap = scaledBitmap
                                            isScanning = false

                                            // Set text recognition in progress
                                            isRecognizingText = true

                                            val image = imageProxy.image
                                            if (image != null) {
                                                val inputImage = InputImage.fromMediaImage(
                                                    image,
                                                    imageProxy.imageInfo.rotationDegrees
                                                )
                                                inputImageWidth = inputImage.width.toFloat()
                                                inputImageHeight = inputImage.height.toFloat()

                                                recognizer.process(inputImage)
                                                    .addOnSuccessListener { result ->
                                                        // Create a map to store word to bounding box mapping
                                                        val wordBoundsMap =
                                                            mutableMapOf<String, android.graphics.Rect>()

                                                        // Extract text blocks, lines, and elements with their bounding boxes
                                                        for (block in result.textBlocks) {
                                                            for (line in block.lines) {
                                                                for (element in line.elements) {

                                                                    element.boundingBox?.let { bounds ->
                                                                        Log.d(
                                                                            "TextRecognition",
                                                                            "Found element: '${element.text}' with bounds: $bounds"
                                                                        )
                                                                        wordBoundsMap[element.text] =
                                                                            bounds
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        val document =
                                                            pipeline.processToCoreDocument(result.text)
                                                        val words = document.tokens().map { token ->
                                                            // Create Word objects with bounds from the map
                                                            val tokenWord = token.word()

                                                            // Use the SpellChecker to check spelling and get suggestions
                                                            val spellCheckResult = spellChecker.checkSpelling(tokenWord)

                                                            Log.d(
                                                                "WordMapping",
                                                                "Token: '$tokenWord', bounds: ${wordBoundsMap[tokenWord]}, " +
                                                                "isCorrect: ${spellCheckResult.isCorrect}, " +
                                                                "suggestions: ${spellCheckResult.suggestions}"
                                                            )

                                                            Word(
                                                                word = tokenWord,
                                                                spellcheckedWord = spellCheckResult.bestSuggestion,
                                                                posTag = token.tag(),
                                                                bounds = wordBoundsMap[tokenWord],
                                                                isSpelledCorrectly = spellCheckResult.isCorrect,
                                                                suggestions = spellCheckResult.suggestions
                                                            )
                                                        }.filter { w: Word ->
                                                            !setOf(
                                                                "NNP",
                                                                "NNPS",
                                                                ",",
                                                                ".",
                                                                "HYPH",
                                                                "``",
                                                                "''",
                                                                ":",
                                                                "RRB-",
                                                                "LRB-"
                                                            ).contains(w.posTag)
                                                        }
                                                            .filter { w: Word ->
                                                                w.word.length > 2 && !w.word.contains(
                                                                    Regex("[0-9]")
                                                                )
                                                            }

                                                        if (words.isNotEmpty()) {
                                                            // Store words with bounds
                                                            wordsWithBounds = words
                                                            // Pass the recognized words to the callback
                                                            onWordsRecognized(words)

                                                            // Check which words are saved
                                                            coroutineScope.launch {
                                                                val savedStatus =
                                                                    mutableMapOf<String, Boolean>()
                                                                words.forEach { word ->
                                                                    savedStatus[word.word] =
                                                                        savedWordsViewModel.isWordSaved(
                                                                            word.word
                                                                        )
                                                                }
                                                                savedWordsStatus = savedStatus
                                                            }

                                                            // Log summary of words with bounds
                                                            val wordsWithValidBounds =
                                                                words.count { it.bounds != null }
                                                            Log.d(
                                                                "WordsWithBounds",
                                                                "Total words: ${words.size}, Words with bounds: $wordsWithValidBounds"
                                                            )
                                                        }

                                                        words.forEach { w ->
                                                            Log.d(
                                                                "CameraActivity",
                                                                "word : '${w.word}', bounds: ${w.bounds}"
                                                            )
                                                        }

                                                        // Text recognition is complete
                                                        isRecognizingText = false
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e(
                                                            "CameraActivity",
                                                            "Text recognition failed",
                                                            e
                                                        )
                                                        isRecognizingText = false
                                                    }
                                            } else {
                                                Log.e("CameraActivity", "Image is null")
                                                isRecognizingText = false
                                            }

                                            // Close the image to release resources
                                            imageProxy.close()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e(
                                                "CameraActivity",
                                                "Image capture failed",
                                                exception
                                            )
                                            isScanning = false
                                        }
                                    }
                                )
                            } else {
                                Log.e("CameraActivity", "ImageCapture is null, cannot take picture")
                                isScanning = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    enabled = !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(text = stringResource(R.string.scan_button))
                    }
                }
            }
        }
    }
}

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
