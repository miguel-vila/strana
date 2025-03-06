package com.mglvl.strana.datacollection

import android.content.Intent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mglvl.strana.R
import com.mglvl.strana.camera.Word
import com.mglvl.strana.camera.WordBoundingBoxOverlay
import com.mglvl.strana.camera.WordProcessor
import com.mglvl.strana.camera.toScaledBitmap
import com.mglvl.strana.viewmodel.SavedWordsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DataCollectionScreen(
    modifier: Modifier = Modifier,
    savedWordsViewModel: SavedWordsViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // State variables
    var isCameraActive by remember { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedWords by remember { mutableStateOf<List<Word>>(emptyList()) }
    var originalText by remember { mutableStateOf("") }
    var collectedWordData by remember { mutableStateOf<List<CollectedWordData>>(emptyList()) }
    var selectedWord by remember { mutableStateOf<Word?>(null) }
    
    // State for results dialog
    var showResultsDialog by remember { mutableStateOf(false) }
    var currentSession by remember { mutableStateOf<CollectionSession?>(null) }
    
    // State for help dialog
    var showHelpDialog by remember { mutableStateOf(false) }
    
    // Create column with camera preview/image at top and word editing below
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Camera or captured image area (60% of screen)
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)) {
                if (isCameraActive) {
                    // Show camera preview with scan button
                    CameraPreview(
                        onImageCaptured = { bitmap, words, fullText ->
                            capturedBitmap = bitmap
                            recognizedWords = words
                            originalText = fullText
                            isCameraActive = false
                            
                            // Initialize collected word data from recognized words
                            // Include ALL recognized words, not just those flagged as strange
                            collectedWordData = words.map { word ->
                                CollectedWordData(
                                    originalWord = word.word,
                                    correctedWord = word.spellcheckedWord,
                                    isMarkedStrange = false, // Default to not strange
                                    bounds = word.bounds,
                                    posTag = word.posTag
                                )
                            }
                        }
                    )
                } else {
                    // Show captured image with word overlays
                    capturedBitmap?.let { bitmap ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Show the captured image
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Captured Image",
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Show word bounding boxes
                            WordBoundingBoxOverlay(
                                modifier = Modifier.fillMaxSize(),
                                words = recognizedWords,
                                inputImageWidth = bitmap.width.toFloat(),
                                inputImageHeight = bitmap.height.toFloat(),
                                savedWordsStatus = collectedWordData.associate { 
                                    it.originalWord to it.isMarkedStrange 
                                },
                                onWordSelected = { word ->
                                    selectedWord = word
                                }
                            )
                        }
                    }
                }
            }
            
            // Word editing area (40% of screen)
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .padding(16.dp)) {
                if (isCameraActive) {
                    // Show instructions
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Scan text to collect word data",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showHelpDialog = true }) {
                            Text("Help")
                        }
                    }
                } else if (selectedWord != null) {
                    // Show word editor
                    WordEditorBox(
                        word = selectedWord!!,
                        collectedData = collectedWordData.find { it.originalWord == selectedWord!!.word },
                        onDataChanged = { updatedData ->
                            // Update the collected data for this word
                            collectedWordData = collectedWordData.map {
                                if (it.originalWord == selectedWord!!.word) updatedData else it
                            }
                        },
                        onClose = {
                            selectedWord = null
                        }
                    )
                } else {
                    // Show word list summary
                    CollectedWordsList(
                        words = collectedWordData,
                        onWordSelected = { wordData ->
                            selectedWord = recognizedWords.find { it.word == wordData.originalWord }
                        }
                    )
                }
            }
            
            // Bottom action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        // Discard current results and return to camera
                        isCameraActive = true
                        capturedBitmap = null
                        recognizedWords = emptyList()
                        originalText = ""
                        collectedWordData = emptyList()
                        selectedWord = null
                    },
                    enabled = !isCameraActive
                ) {
                    Text("Scan Again")
                }
                
                Button(
                    onClick = {
                        if (!isCameraActive) {
                            // Generate corrected text by replacing original words with their corrections
                            val correctedText = generateCorrectedText(originalText, collectedWordData)
                            
                            // Create session
                            val session = CollectionSession(
                                words = collectedWordData,
                                fullText = correctedText
                            )
                            
                            // Save results to file
                            try {
                                DataCollectionStorage.saveCollectionSession(context, session)
                                currentSession = session
                                showResultsDialog = true
                                
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Session saved successfully")
                                }
                            } catch (e: Exception) {
                                Log.e("DataCollection", "Failed to save session", e)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to save session: ${e.message}")
                                }
                            }
                        }
                    },
                    enabled = !isCameraActive && collectedWordData.isNotEmpty()
                ) {
                    Text("Save Results")
                }
            }
        }
        
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data
            )
        }
        
        // Results dialog
        if (showResultsDialog && currentSession != null) {
            CollectionResultsDialog(
                session = currentSession!!,
                onDismiss = { showResultsDialog = false },
                onExport = {
                    shareExportedData(context)
                    showResultsDialog = false
                }
            )
        }
        
        // Help dialog
        if (showHelpDialog) {
            DataCollectionHelpDialog(
                onDismiss = { showHelpDialog = false }
            )
        }
    }
}

@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap, List<Word>, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Create a remembered ImageCapture instance that persists across recompositions
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Create the text recognizer
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    
    // Create the word processor
    val wordProcessor = remember { WordProcessor(context) }

    // State to track if scanning is in progress
    var isScanning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
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
                        Log.e("DataCollectionActivity", "Use case binding failed", e)
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
                    Log.d("DataCollectionActivity", "imageCapture: ${currentImageCapture != null}")

                    if (currentImageCapture != null) {
                        currentImageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    Log.d("DataCollectionActivity", "Image captured successfully")

                                    // Convert the captured image to bitmap and store it immediately
                                    val scaledBitmap = imageProxy.toScaledBitmap()
                                    
                                    // Set text recognition in progress
                                    val image = imageProxy.image
                                    if (image != null) {
                                        val inputImage = InputImage.fromMediaImage(
                                            image,
                                            imageProxy.imageInfo.rotationDegrees
                                        )

                                        recognizer.process(inputImage)
                                            .addOnSuccessListener { result ->
                                                // Process the text recognition result using WordProcessor
                                                val words = wordProcessor.processTextRecognitionResult(result)
                                                
                                                // Call the callback with the bitmap, words, and full text
                                                onImageCaptured(
                                                    scaledBitmap,
                                                    words,
                                                    result.text
                                                )
                                                
                                                isScanning = false
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e(
                                                    "DataCollectionActivity",
                                                    "Text recognition failed",
                                                    e
                                                )
                                                isScanning = false
                                            }
                                    } else {
                                        Log.e("DataCollectionActivity", "Image is null")
                                        isScanning = false
                                    }

                                    // Close the image to release resources
                                    imageProxy.close()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e(
                                        "DataCollectionActivity",
                                        "Image capture failed",
                                        exception
                                    )
                                    isScanning = false
                                }
                            }
                        )
                    } else {
                        Log.e("DataCollectionActivity", "ImageCapture is null, cannot take picture")
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

@Composable
fun WordEditorBox(
    word: Word,
    collectedData: CollectedWordData?,
    onDataChanged: (CollectedWordData) -> Unit,
    onClose: () -> Unit
) {
    var correctedText by remember { 
        mutableStateOf(collectedData?.correctedWord ?: word.spellcheckedWord ?: word.word) 
    }
    var isMarkedStrange by remember { mutableStateOf(collectedData?.isMarkedStrange ?: false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Edit Word",
                style = MaterialTheme.typography.titleMedium
            )
            
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Original word: ${word.word}",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = correctedText,
            onValueChange = { 
                correctedText = it
                onDataChanged(
                    CollectedWordData(
                        originalWord = word.word,
                        correctedWord = if (correctedText != word.word) correctedText else null,
                        isMarkedStrange = isMarkedStrange,
                        bounds = word.bounds,
                        posTag = word.posTag
                    )
                )
            },
            label = { Text("Corrected word") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isMarkedStrange,
                onCheckedChange = { checked ->
                    isMarkedStrange = checked
                    onDataChanged(
                        CollectedWordData(
                            originalWord = word.word,
                            correctedWord = if (correctedText != word.word) correctedText else null,
                            isMarkedStrange = isMarkedStrange,
                            bounds = word.bounds,
                            posTag = word.posTag
                        )
                    )
                }
            )
            Text("Mark as strange word")
        }
    }
}

@Composable
fun CollectedWordsList(
    words: List<CollectedWordData>,
    onWordSelected: (CollectedWordData) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Recognized Words",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        if (words.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No words recognized")
            }
        } else {
            Text(
                text = "Tap a word to edit. Strange words are highlighted.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(words) { wordData ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (wordData.isMarkedStrange) 
                                    MaterialTheme.colorScheme.errorContainer
                                else 
                                    MaterialTheme.colorScheme.surface
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)) {
                            Text(
                                text = wordData.correctedWord ?: wordData.originalWord,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (wordData.correctedWord != null) {
                                Text(
                                    text = "(original: ${wordData.originalWord})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        if (wordData.isMarkedStrange) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Strange word",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        TextButton(onClick = { onWordSelected(wordData) }) {
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionResultsDialog(
    session: CollectionSession,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collection Results") },
        text = {
            Column {
                Text("Session ID: ${session.id}")
                Text("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(session.timestamp))}")
                Text("Words collected: ${session.words.size}")
                Text("Strange words: ${session.words.count { it.isMarkedStrange }}")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Strange words:", fontWeight = FontWeight.Bold)
                
                val strangeWords = session.words.filter { it.isMarkedStrange }
                if (strangeWords.isEmpty()) {
                    Text("No words marked as strange")
                } else {
                    strangeWords.forEach { word ->
                        Text("• ${word.correctedWord ?: word.originalWord}")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onExport) {
                Text("Export Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DataCollectionHelpDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data Collection Help") },
        text = {
            Column {
                Text("This feature helps collect data about which words you consider 'strange'.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Steps:", fontWeight = FontWeight.Bold)
                Text("1. Scan text with the camera")
                Text("2. Tap words to edit them")
                Text("3. Correct any misrecognized words")
                Text("4. Check the box for words you consider strange")
                Text("5. Save results when finished")
                Text("6. Export data to your computer for analysis")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

/**
 * Generate corrected text by replacing original words with their corrections
 */
fun generateCorrectedText(originalText: String, wordData: List<CollectedWordData>): String {
    var correctedText = originalText
    
    // Sort words by length (descending) to avoid replacing substrings of longer words
    val sortedWords = wordData
        .filter { it.correctedWord != null }
        .sortedByDescending { it.originalWord.length }
    
    // Replace each original word with its correction
    for (word in sortedWords) {
        correctedText = correctedText.replace(
            "\\b${word.originalWord}\\b".toRegex(),
            word.correctedWord ?: word.originalWord
        )
    }
    
    return correctedText
}

/**
 * Share exported data as CSV
 */
fun shareExportedData(context: Context) {
    // Export all data to a CSV file
    val fileUri = DataCollectionStorage.exportAllDataToCsv(context) ?: return
    
    // Create intent to share the file
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Strana Collected Word Data")
        putExtra(Intent.EXTRA_STREAM, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    // Start the activity to share the file
    context.startActivity(Intent.createChooser(intent, "Share Word Data"))
}
