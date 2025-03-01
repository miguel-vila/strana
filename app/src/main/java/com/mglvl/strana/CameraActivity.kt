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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
        // Camera preview takes up the top half
        CameraPreview(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
        
        // Words and definitions area takes up the bottom half
        WordsAndDefinitionsArea(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = remember { CoroutineScope(Dispatchers.Main) }
    var imageCapture: ImageCapture? = remember { null }
    
    DisposableEffect(lifecycleOwner) {
        val job = coroutineScope.launch {
            while (isActive) {
                delay(5000) // 5 seconds
                imageCapture?.let { capture ->
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // Here you have access to the image bytes
                                // You can process the image here
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                
                                // Log the size of the captured image
                                Log.d("CameraActivity", "Image captured: ${bytes.size} bytes")
                                
                                // TODO: Do something with the bytes
                                
                                // Close the image to release resources
                                image.close()
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
                WordDefinition("Serendipity", "The occurrence and development of events by chance in a happy or beneficial way."),
                WordDefinition("Ubiquitous", "Present, appearing, or found everywhere."),
                WordDefinition("Mellifluous", "Sweet or musical; pleasant to hear."),
                WordDefinition("Quintessential", "Representing the most perfect or typical example of a quality or class.")
            )
        }
        
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            wordsAndDefinitions.forEach { wordDef ->
                WordDefinitionCard(wordDef)
                Spacer(modifier = Modifier.height(8.dp))
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
