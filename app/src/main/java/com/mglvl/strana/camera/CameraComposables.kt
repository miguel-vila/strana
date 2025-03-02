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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mglvl.strana.R
import com.mglvl.strana.dictionary.DictionaryApiClient
import com.mglvl.strana.dictionary.WordDefinition
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.Properties

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    dictionaryApiClient: DictionaryApiClient
) {
    // State for selected word and its definition
    var selectedWord by remember { mutableStateOf<Word?>(null) }
    var selectedWordDefinition by remember { mutableStateOf<WordDefinition?>(null) }
    
    // Shared state for recognized words
    var recognizedWords by remember { mutableStateOf<List<Word>>(emptyList()) }
    
    // State to track if camera is active (no image captured yet)
    var isCameraActive by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize()) {
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
                dictionaryApiClient.getDefinition(word.word) { definition ->
                    selectedWordDefinition = definition
                }
            },
            onCameraStateChanged = { isActive ->
                isCameraActive = isActive
                // Reset word selection when returning to camera
                if (isActive) {
                    selectedWord = null
                    selectedWordDefinition = null
                }
            }
        )

        // Words and definitions area takes up the bottom 40%
        WordsAndDefinitionsArea(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f),
            selectedWord = selectedWord,
            selectedWordDefinition = selectedWordDefinition,
            isCameraActive = isCameraActive,
            hasStrangeWords = recognizedWords.any { StrangeWordConfig.isStrange(it.word) }
        )
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onWordsRecognized: (List<Word>) -> Unit,
    onWordSelected: (Word) -> Unit,
    onCameraStateChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create a remembered ImageCapture instance that persists across recompositions
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Create the text recognizer
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // State to track if scanning is in progress
    var isScanning by remember { mutableStateOf(false) }

    // State to hold the captured image bitmap
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // State to hold container size for scaling calculations
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // State to hold the recognized words with their bounding boxes
    var wordsWithBounds by remember { mutableStateOf<List<Word>>(emptyList()) }

    // State to track the scaling factor applied to the original image
    var inputImageWidth by remember { mutableStateOf(1f) }
    var inputImageHeight by remember { mutableStateOf(1f) }

    val props = Properties()
    props.setProperty("annotators", "tokenize,pos")
    val pipeline = StanfordCoreNLP(props)
    
    // Update camera state whenever capturedBitmap changes
    onCameraStateChanged(capturedBitmap == null)

    Box(modifier = modifier.onSizeChanged { containerSize = it }) {
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

                // Draw bounding boxes overlay with touch detection
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(wordsWithBounds) {
                            detectTapGestures { tapOffset ->
                                // Calculate scale factors for touch coordinates
                                val widthScalingFactor = size.width / inputImageWidth
                                val heightScalingFactor = size.height / inputImageHeight

                                // Check if the tap is inside any word bounding box
                                val strangeWords = wordsWithBounds.filter { StrangeWordConfig.isStrange(it.word) }

                                var wordSelected = false
                                for (word in strangeWords) {
                                    if (wordSelected) break
                                    word.bounds?.let { rect ->
                                        val left = rect.left.toFloat() * widthScalingFactor
                                        val top = rect.top.toFloat() * heightScalingFactor
                                        val width = (rect.right - rect.left).toFloat() * widthScalingFactor
                                        val height = (rect.bottom - rect.top).toFloat() * heightScalingFactor

                                        // Create a Compose Rect for easier hit testing
                                        val wordRect = Rect(left, top, left + width, top + height)

                                        if (wordRect.contains(tapOffset)) {
                                            onWordSelected(word)
                                            wordSelected = true
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // Calculate scale factors for bounding boxes
                    val bitmap = capturedBitmap ?: return@Canvas
                    val widthScalingFactor = size.width / inputImageWidth
                    val heightScalingFactor = size.height / inputImageHeight

                    Log.d(
                        "Canvas",
                        "Drawing canvas overlay. Image size: ${bitmap.width}x${bitmap.height}, " +
                                "Canvas size: ${size.width}x${size.height}, Scale: $widthScalingFactor, $heightScalingFactor"
                    )

                    // Draw rectangles around strange words
                    val strangeWords =
                        wordsWithBounds.filter { StrangeWordConfig.isStrange(it.word) }

                    strangeWords.forEach { word ->
                        word.bounds?.let { rect ->
                            val left = rect.left.toFloat() * widthScalingFactor
                            val top = rect.top.toFloat() * heightScalingFactor
                            val width = (rect.right - rect.left).toFloat() * widthScalingFactor
                            val height = (rect.bottom - rect.top).toFloat() * heightScalingFactor

                            // Draw rectangle around the word
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                style = Stroke(width = 5f) // Increased width for better visibility
                            )
                        }
                    }
                }
                
                // Position the button at the bottom right when showing captured image
                Button(
                    onClick = {
                        capturedBitmap = null
                        wordsWithBounds = emptyList()
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

                                            // Convert the captured image to bitmap and store it
                                            val scaledBitmap = imageProxy.toScaledBitmap()
                                            capturedBitmap = scaledBitmap

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
                                                        val wordBoundsMap = mutableMapOf<String, android.graphics.Rect>()

                                                        // Extract text blocks, lines, and elements with their bounding boxes
                                                        for (block in result.textBlocks) {
                                                            for (line in block.lines) {
                                                                for (element in line.elements) {

                                                                    element.boundingBox?.let { bounds ->
                                                                        Log.d(
                                                                            "TextRecognition",
                                                                            "Found element: '${element.text}' with bounds: $bounds"
                                                                        )
                                                                        wordBoundsMap[element.text] = bounds
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        val document =
                                                            pipeline.processToCoreDocument(result.text)
                                                        val words = document.tokens().map { token ->
                                                            // Create Word objects with bounds from the map
                                                            val tokenWord = token.word()
                                                            val bounds = wordBoundsMap[tokenWord]

                                                            Log.d(
                                                                "WordMapping",
                                                                "Token: '$tokenWord', bounds: $bounds"
                                                            )

                                                            Word(
                                                                word = tokenWord,
                                                                posTag = token.tag(),
                                                                bounds = bounds
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
                                                                w.word.length > 2 && !w.word.contains(Regex("[0-9]"))
                                                            }

                                                        if (words.isNotEmpty()) {
                                                            // Store words with bounds
                                                            wordsWithBounds = words
                                                            // Pass the recognized words to the callback
                                                            onWordsRecognized(words)

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

                                                        // Only reset scanning state, keep the bitmap
                                                        isScanning = false
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e(
                                                            "CameraActivity",
                                                            "Text recognition failed",
                                                            e
                                                        )
                                                        isScanning = false
                                                        // Keep the bitmap even on failure
                                                    }
                                            } else {
                                                Log.e("CameraActivity", "Image is null")
                                                isScanning = false
                                                capturedBitmap = null
                                            }

                                            // Close the image to release resources
                                            imageProxy.close()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("CameraActivity", "Image capture failed", exception)
                                            isScanning = false
                                            capturedBitmap = null
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
    hasStrangeWords: Boolean
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
                    WordDefinitionCard(
                        word = selectedWord.word,
                        definition = selectedWordDefinition
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
    definition: WordDefinition?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = word,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

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
            } else if (definition.definition == null) {
                // Show error message in red when definition is not found (404)
                Text(
                    text = "Meaning couldn't be found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorResource(id = R.color.red)
                )
            } else {
                // Show part of speech if available
                definition.partOfSpeech?.let { pos ->
                    Text(
                        text = pos,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Show definition
                Text(
                    text = definition.definition,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
