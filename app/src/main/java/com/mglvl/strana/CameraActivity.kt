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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mglvl.strana.ui.theme.StranaTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CameraActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapturingJob: Job? = null

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

        setContent {
            StranaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraScreen(modifier = Modifier.padding(innerPadding))
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
fun CameraScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Camera preview takes up the top 75%
        CameraPreview(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f)
        )

        // Words and definitions area takes up the bottom 40%
        WordsAndDefinitionsArea(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
        )
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
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
                                            var words = result.text.split(Regex("[^\\p{L}']+"))
                                                .filter { w ->
                                                    w.isNotEmpty()
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

// Data class to hold word and definition pairs
data class WordDefinition(
    val word: String,
    val definition: String
)

@Composable
fun WordsAndDefinitionsArea(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        // Sample words and definitions
        val wordsAndDefinitions = remember {
            listOf(
                WordDefinition("Ephemeral", "Lasting for a very short time."),
                WordDefinition(
                    "Serendipity",
                    "The occurrence and development of events by chance in a happy or beneficial way."
                ),
                WordDefinition("Ubiquitous", "Present, appearing, or found everywhere."),
                WordDefinition("Mellifluous", "Sweet or musical; pleasant to hear."),
                WordDefinition(
                    "Quintessential",
                    "Representing the most perfect or typical example of a quality or class."
                )
            )
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxSize()
        ) {
            // Header text showing number of words found
            Text(
                text = "I found ${wordsAndDefinitions.size} unusual words!",
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
                wordsAndDefinitions.forEach { wordDef ->
                    WordDefinitionCard(wordDef)
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
                        text = "Scroll down for more words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun WordDefinitionCard(wordDefinition: WordDefinition) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = wordDefinition.word,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = wordDefinition.definition,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
