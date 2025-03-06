# Word Data Collection Feature Implementation Plan

## Overview

This plan outlines the implementation of a new feature to collect data for improving "strange word" detection. The feature will allow users to scan text, manually correct recognized words, and mark words as "strange" or not. The collected data can be exported for analysis.

## Implementation Details

### 1. Create Data Collection Models

Create a model to represent collected word data:

```kotlin
// app/src/main/java/com/mglvl/strana/datacollection/CollectedWordData.kt
data class CollectedWordData(
    val originalWord: String,
    val correctedWord: String?, // null if no correction needed
    val isMarkedStrange: Boolean,
    val bounds: android.graphics.Rect? = null,
    val posTag: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CollectionSession(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val words: List<CollectedWordData>,
    val fullText: String // The complete recognized text with corrections
)
```

### 2. Add Data Collection Activity and Layout

Create a new activity for data collection:

```kotlin
// app/src/main/java/com/mglvl/strana/datacollection/DataCollectionActivity.kt
class DataCollectionActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private val wordProcessor = WordProcessor(context)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setContent {
            StranaTheme {
                Scaffold { innerPadding ->
                    DataCollectionScreen(
                        modifier = Modifier.padding(innerPadding)
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
```

### 3. Create Data Collection UI Components

Create the data collection screen:

```kotlin
// app/src/main/java/com/mglvl/strana/datacollection/DataCollectionComposables.kt
@Composable
fun DataCollectionScreen(modifier: Modifier = Modifier) {
    // State variables
    var isCameraActive by remember { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedWords by remember { mutableStateOf<List<Word>>(emptyList()) }
    var originalText by remember { mutableStateOf("") }
    var collectedWordData by remember { mutableStateOf<List<CollectedWordData>>(emptyList()) }
    var selectedWord by remember { mutableStateOf<Word?>(null) }
    
    // Create column with camera preview/image at top and word editing below
    Column(modifier = modifier.fillMaxSize()) {
        // Camera or captured image area (60% of screen)
        Box(modifier = Modifier.weight(0.6f)) {
            if (isCameraActive) {
                // Show camera preview with scan button
                CameraPreviewWithScanButton(
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
                CapturedImageWithWordOverlays(
                    bitmap = capturedBitmap!!,
                    words = recognizedWords,
                    onWordSelected = { selectedWord = it }
                )
            }
        }
        
        // Word editing area (40% of screen)
        Box(modifier = Modifier.weight(0.4f)) {
            if (isCameraActive) {
                // Show instructions
                Text("Scan text to collect word data")
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
                    }
                )
            } else {
                // Show word list summary
                CollectedWordsList(
                    words = collectedWordData,
                    onWordSelected = { word ->
                        selectedWord = recognizedWords.find { it.word == word.originalWord }
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
            Button(onClick = {
                // Discard current results and return to camera
                isCameraActive = true
                capturedBitmap = null
                recognizedWords = emptyList()
                originalText = ""
                collectedWordData = emptyList()
                selectedWord = null
            }) {
                Text("Scan Again")
            }
            
            Button(onClick = {
                // Generate corrected text by replacing original words with their corrections
                val correctedText = generateCorrectedText(originalText, collectedWordData)
                
                // Save results to file and display summary
                saveCollectionSession(CollectionSession(
                    words = collectedWordData,
                    fullText = correctedText
                ))
            }) {
                Text("Save Results")
            }
        }
    }
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

@Composable
fun WordEditorBox(
    word: Word,
    collectedData: CollectedWordData?,
    onDataChanged: (CollectedWordData) -> Unit
) {
    var correctedText by remember { mutableStateOf(collectedData?.correctedWord ?: word.word) }
    var isMarkedStrange by remember { mutableStateOf(collectedData?.isMarkedStrange ?: false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Original word: ${word.word}",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
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
            label = { Text("Corrected word") }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
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
```

### 4. Implement Data Storage and Export

Create a utility for saving and exporting the collected data:

```kotlin
// app/src/main/java/com/mglvl/strana/datacollection/DataCollectionStorage.kt
object DataCollectionStorage {
    private const val DIRECTORY_NAME = "strana_data_collection"
    private const val FILE_PREFIX = "session_"
    private const val FILE_EXTENSION = ".json"
    private const val CSV_FILE_NAME = "strana_collected_data.csv"
    
    // Save a collection session to JSON file
    fun saveCollectionSession(context: Context, session: CollectionSession): Uri {
        // Ensure directory exists
        val directory = File(context.getExternalFilesDir(null), DIRECTORY_NAME)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        // Create JSON file
        val filename = "$FILE_PREFIX${session.id}$FILE_EXTENSION"
        val file = File(directory, filename)
        
        // Convert session to JSON
        val json = Gson().toJson(session)
        file.writeText(json)
        
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    // Get all saved sessions
    fun getSavedSessions(context: Context): List<CollectionSession> {
        val directory = File(context.getExternalFilesDir(null), DIRECTORY_NAME)
        if (!directory.exists()) return emptyList()
        
        return directory.listFiles { file -> 
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_EXTENSION)
        }?.mapNotNull { file ->
            try {
                val json = file.readText()
                Gson().fromJson(json, CollectionSession::class.java)
            } catch (e: Exception) {
                Log.e("DataCollection", "Failed to parse session file: ${file.name}", e)
                null
            }
        } ?: emptyList()
    }
    
    // Export all data to a CSV file that can be shared
    fun exportAllDataToCsv(context: Context): Uri? {
        val sessions = getSavedSessions(context)
        if (sessions.isEmpty()) return null
        
        val csvFile = File(context.cacheDir, CSV_FILE_NAME)
        if (csvFile.exists()) {
            csvFile.delete()
        }
        
        try {
            csvFile.bufferedWriter().use { writer ->
                // Write CSV header
                writer.write("SessionID,Timestamp,FullText,StrangeWords\n")
                
                // Write each session as a row
                sessions.forEach { session ->
                    val strangeWords = session.words
                        .filter { it.isMarkedStrange }
                        .map { it.correctedWord ?: it.originalWord }
                        .joinToString(";")
                    
                    // Escape fields for CSV
                    val escapedFullText = "\"${session.fullText.replace("\"", "\"\"")}\""
                    val escapedStrangeWords = "\"${strangeWords.replace("\"", "\"\"")}\""
                    
                    writer.write("${session.id},${session.timestamp},${escapedFullText},${escapedStrangeWords}\n")
                }
            }
            
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
        } catch (e: Exception) {
            Log.e("DataCollection", "Failed to create CSV file", e)
            return null
        }
    }
}
```

### 5. Implement Results View

Create a component to display collected data after saving:

```kotlin
@Composable
fun CollectionResultsScreen(
    session: CollectionSession,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Collection Results",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Session ID: ${session.id}")
        Text("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(session.timestamp))}")
        Text("Words collected: ${session.words.size}")
        Text("Strange words: ${session.words.count { it.isMarkedStrange }}")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Word list with strange words highlighted
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(session.words) { wordData ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (wordData.isMarkedStrange) 
                                MaterialTheme.colorScheme.errorContainer
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wordData.originalWord, fontWeight = FontWeight.Bold)
                        wordData.correctedWord?.let {
                            Text("→ $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (wordData.isMarkedStrange) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Strange word",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onDismiss) {
                Text("Close")
            }
            
            Button(onClick = onExport) {
                Text("Export Data")
            }
        }
    }
}
```

### 6. Modify MainActivity

Add a new button to the main menu:

```kotlin
// Modify app/src/main/java/com/mglvl/strana/MainActivity.kt
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    val intent = Intent(context, CameraActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text(text = "Capture")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val intent = Intent(context, SavedWordsActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text(text = "Saved Words")
            }
            
            // New button for data collection
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val intent = Intent(context, DataCollectionActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text(text = "Collect Word Data")
            }
        }
    }
}
```

### 7. Update AndroidManifest.xml

Add the new activity to the manifest and set up a FileProvider for sharing data:

```xml
<!-- Add to app/src/main/AndroidManifest.xml -->
<activity
    android:name=".datacollection.DataCollectionActivity"
    android:exported="false"
    android:theme="@style/Theme.Strana" />

<!-- Add FileProvider for sharing data -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Create a file_paths.xml resource:

```xml
<!-- app/src/main/res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="data_collection" path="strana_data_collection" />
    <cache-path name="shared_files" path="/" />
</paths>
```

### 8. Implement Data Export

Create a utility function to handle sharing the exported data:

```kotlin
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
```

### 9. Add Help Dialog

Create a help dialog to explain the data collection process:

```kotlin
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
                Spacer(height = 8.dp)
                Text("Steps:")
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
```
