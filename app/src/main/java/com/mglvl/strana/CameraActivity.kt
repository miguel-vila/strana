package com.mglvl.strana

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import edu.stanford.nlp.ling.*
import edu.stanford.nlp.pipeline.*
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.mglvl.strana.dictionary.DictionaryApiClient
import com.mglvl.strana.dictionary.WordDefinition
import com.mglvl.strana.ui.theme.StranaTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Properties
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class CameraActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapturingJob: Job? = null
    private val dictionaryApiClient = DictionaryApiClient()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with camera
        } else {
            // Permission denied, show message or handle accordingly
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request camera permission if not granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize StrangeWordConfig with application context
        StrangeWordConfig.initialize(applicationContext)

        setContent {
            StranaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraScreen(
                        modifier = Modifier.padding(innerPadding),
                        dictionaryApiClient = dictionaryApiClient
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageCapturingJob?.cancel()
        cameraExecutor.shutdown()
    }
}

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    dictionaryApiClient: DictionaryApiClient
) {
    // Shared state for recognized words
    var recognizedWords by remember { mutableStateOf<List<Word>>(emptyList()) }

    Column(modifier = modifier.fillMaxSize()) {
        // Camera preview takes up the top 75%
        CameraPreview(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f),
            onWordsRecognized = { words ->
                recognizedWords = words
            }
        )

        // Words and definitions area takes up the bottom 40%
        WordsAndDefinitionsArea(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f),
            recognizedWords = recognizedWords,
            dictionaryApiClient = dictionaryApiClient
        )
    }
}

data class Word(
    val word: String,
    val posTag: String,
    val bounds: Rect? = null // Add bounds information
)

// Helper function to convert ImageProxy to Bitmap
fun ImageProxy.toScaledBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    // Set inSampleSize to downsample the image during decoding
    val options = BitmapFactory.Options().apply {
        inSampleSize = 4  // Downsample by factor of 4
    }

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    // Rotate the bitmap if needed based on the image rotation
    val rotationDegrees = imageInfo.rotationDegrees
    val rotatedBitmap = if (rotationDegrees != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }

    // Scale down the bitmap to a reasonable size to avoid "Canvas: trying to draw too large bitmap" error
    return scaleBitmap(rotatedBitmap, 800)  // Reduced from 1280 to 800
}

// Helper function to scale down a bitmap while maintaining aspect ratio
fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    // If the bitmap is already smaller than the max dimension, return it as is
    if (width <= maxDimension && height <= maxDimension) {
        return bitmap
    }

    // Calculate the scaling factor
    val scaleFactor = if (width > height) {
        maxDimension.toFloat() / width.toFloat()
    } else {
        maxDimension.toFloat() / height.toFloat()
    }

    // Calculate new dimensions
    val newWidth = (width * scaleFactor).toInt()
    val newHeight = (height * scaleFactor).toInt()

    // Create and return the scaled bitmap
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onWordsRecognized: (List<Word>) -> Unit
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
    var inputImageWidth by remember { mutableStateOf(1) }
    var inputImageHeight by remember { mutableStateOf(1) }

    val props = Properties()
    props.setProperty("annotators", "tokenize,pos")
    val pipeline = StanfordCoreNLP(props)

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

                // Draw bounding boxes overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
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
                            val right = left + width
                            val bottom = top + height

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
                                                inputImageWidth = inputImage.width
                                                inputImageHeight = inputImage.height

                                                recognizer.process(inputImage)
                                                    .addOnSuccessListener { result ->
                                                        // Create a map to store word to bounding box mapping
                                                        val wordBoundsMap = mutableMapOf<String, Rect>()

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
                                                                w.word.length > 2 && !w.word.all { c -> c.isDigit() }
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

// Configuration for what makes a word "strange"
object StrangeWordConfig {
    private val commonWords = mutableSetOf<String>()
    private const val TOP_WORDS_COUNT = 40_000
    private var isInitialized = false

    fun initialize(context: android.content.Context) {
        if (isInitialized) return

        try {
            val inputStream = context.assets.open("en_50k.txt")
            val reader = inputStream.bufferedReader()

            var count = 0
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (count < TOP_WORDS_COUNT) {
                        val word = line.split(" ").get(0).trim().lowercase()
                        commonWords.add(word)
                        count++
                    } else {
                        return@forEach
                    }
                }
            }

            isInitialized = true
            Log.d("StrangeWordConfig", "Loaded $count common words")

        } catch (e: Exception) {
            Log.e("StrangeWordConfig", "Error loading word list", e)
            throw e
        }
    }

    // Function to determine if a word is strange based on current config
    fun isStrange(word: String): Boolean {
        return !commonWords.contains(word.lowercase())
    }
}

@Composable
fun WordsAndDefinitionsArea(
    modifier: Modifier = Modifier,
    recognizedWords: List<Word>,
    dictionaryApiClient: DictionaryApiClient
) {
    val STRANGE_WORDS_LIMIT = 15
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        // Filter for strange words based on our configuration
        val strangeWords = remember(recognizedWords) {
            recognizedWords
                .filter { StrangeWordConfig.isStrange(it.word) }
                .take(STRANGE_WORDS_LIMIT)
                .distinctBy { it.word }
        }

        // State to hold word definitions
        var wordDefinitions by remember { mutableStateOf<Map<String, WordDefinition?>>(emptyMap()) }

        // Fetch definitions for strange words
        DisposableEffect(strangeWords) {
            strangeWords.forEach { word ->
                if (!wordDefinitions.containsKey(word.word)) {
                    // Set loading state
                    wordDefinitions = wordDefinitions + (word.word to null)

                    // Fetch definition
                    dictionaryApiClient.getDefinition(word.word) { definition ->
                        wordDefinitions = wordDefinitions + (word.word to definition)
                    }
                }
            }

            onDispose { }
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxSize()
        ) {
            // Header text showing number of words found
            Text(
                text = stringResource(R.string.found_words, strangeWords.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Scrollable content with words and definitions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                strangeWords.forEach { word ->
                    val definition = wordDefinitions[word.word]
                    WordDefinitionCard(word.word, definition)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Add extra space at the bottom to make scrolling more obvious
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Visual indicator that there's more content to scroll
            if (!scrollState.canScrollForward && scrollState.value > 0) {
                // We're at the bottom
            } else if (!scrollState.canScrollBackward && scrollState.value == 0) {
                // We're at the top, show indicator that there's more to scroll
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.scroll_for_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
