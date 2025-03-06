package com.mglvl.strana.datacollection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mglvl.strana.camera.StrangeWordConfig
import com.mglvl.strana.ui.theme.StranaTheme
import com.mglvl.strana.viewmodel.SavedWordsViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DataCollectionActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private val savedWordsViewModel: SavedWordsViewModel by viewModels()

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
                    DataCollectionScreen(
                        modifier = Modifier.padding(innerPadding),
                        savedWordsViewModel = savedWordsViewModel
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
