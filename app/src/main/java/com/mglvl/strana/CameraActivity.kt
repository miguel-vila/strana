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
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    var recognizedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    
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

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onWordsRecognized: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = remember { CoroutineScope(Dispatchers.Main) }
    var imageCapture: ImageCapture? = remember { null }
    var recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    DisposableEffect(lifecycleOwner) {
        val job = coroutineScope.launch {
            while (isActive) {
                delay(5000) // 5 seconds
                imageCapture?.let { capture ->
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                // Log the size of the captured image
                                Log.d("CameraActivity", "Image captured")

                                var image = imageProxy.image
                                if (image != null) {
                                    var inputImage = InputImage.fromMediaImage(
                                        image,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    recognizer.process(inputImage)
                                        .addOnSuccessListener { result ->
                                            val words = result.text.split(Regex("[^\\p{L}']+"))
                                                .filter { w ->
                                                    w.isNotEmpty()
                                                }
                                                .map { w ->
                                                    // normalize
                                                    w.lowercase()
                                                }

                                            if (words.isNotEmpty()) {
                                                // Pass the recognized words to the callback
                                                onWordsRecognized(words)
                                            }
                                            
                                            words.forEach { w ->
                                                Log.d("CameraActivity", "word : '${w}'")
                                            }
                                        }
                                }

                                // Close the image to release resources
                                imageProxy.close()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraActivity", "Image capture failed", exception)
                            }
                        }
                    )
                }
            }
        }

        onDispose {
            job.cancel()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Set up the image capture use case
                imageCapture = ImageCapture.Builder()
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("CameraActivity", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

// Configuration for what makes a word "strange"
object StrangeWordConfig {
    private val commonWords = mutableSetOf<String>()
    private const val TOP_WORDS_COUNT = 25000
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
                        commonWords.add(line.trim().lowercase())
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
    recognizedWords: List<String>,
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
                .filter { StrangeWordConfig.isStrange(it) }
                .take(STRANGE_WORDS_LIMIT)
                .distinct()
        }
        
        // State to hold word definitions
        var wordDefinitions by remember { mutableStateOf<Map<String, WordDefinition?>>(emptyMap()) }
        
        // Fetch definitions for strange words
        DisposableEffect(strangeWords) {
            strangeWords.forEach { word ->
                if (!wordDefinitions.containsKey(word)) {
                    // Set loading state
                    wordDefinitions = wordDefinitions + (word to null)
                    
                    // Fetch definition
                    dictionaryApiClient.getDefinition(word) { definition ->
                        wordDefinitions = wordDefinitions + (word to definition)
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
                    val definition = wordDefinitions[word]
                    WordDefinitionCard(word, definition)
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
                
                // Show definition or fallback message
                Text(
                    text = definition.definition ?: "No definition found",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
