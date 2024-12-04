package com.example.adskipper2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.adskipper2.gesture.GestureAction
import com.example.adskipper2.gesture.GesturePlayer
import com.example.adskipper2.gesture.GestureRecorder
import com.example.adskipper2.ui.compose.AdSkipperApp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

data class AppInfo(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var gestureRecorder: GestureRecorder
    private lateinit var gesturePlayer: GesturePlayer
    private lateinit var projectionManager: MediaProjectionManager

    private val selectedApps = mutableStateOf(setOf<AppInfo>())
    private val selectedContent = mutableStateOf(setOf<String>())
    private val recognizedContent = mutableStateOf("")
    private val isServiceRunning = mutableStateOf(false)
    private val currentMediaType = mutableStateOf("טקסט")
    private val availableApps = mutableStateOf(listOf<AppInfo>())
    private val isRecording = mutableStateOf(false)
    private var recordedActions: List<GestureAction> = emptyList()

    companion object {
        private const val TAG = "MainActivity"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }

    private val startScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startTextDetectionService(result.resultCode, result.data!!)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            Log.d(TAG, "All permissions granted")
            initializeApp()
        } else {
            Log.e(TAG, "Permissions not granted: ${permissions.filterValues { !it }.keys}")
            Toast.makeText(this, "נדרשות הרשאות להפעלת האפליקציה", Toast.LENGTH_LONG).show()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            Log.d(TAG, "Image selected: $uri")
            if (checkStoragePermission()) saveSelectedImage(it)
            else requestStoragePermission()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains("${packageName}/${SkipperService::class.java.name}") == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service", e)
            return false
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings", e)
            Toast.makeText(this, getString(R.string.settings_open_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "App started")
            initializeComponents()
            setupUI()
            setupTouchListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "שגיאה באתחול האפליקציה", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeComponents() {
        Log.d(TAG, "Initializing components")
        prefs = getSharedPreferences("targets", MODE_PRIVATE)
        gestureRecorder = GestureRecorder()
        gesturePlayer = GesturePlayer(this)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        loadInstalledApps()
        checkRequiredPermissions()
        initializePrefs()
    }

    private fun initializePrefs() {
        Log.d(TAG, "Initializing preferences")
        prefs.edit().apply {
            putStringSet("selected_apps", setOf("com.google.android.youtube"))
            putString("com.google.android.youtube_text", "דלג על מודעה")
            apply()
        }
    }

    private fun setupUI() {
        Log.d(TAG, "Setting up UI")
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color.White,
                    onPrimary = androidx.compose.ui.graphics.Color.Black,
                    surface = androidx.compose.ui.graphics.Color.Black,
                    onSurface = androidx.compose.ui.graphics.Color.White,
                    background = androidx.compose.ui.graphics.Color.Black,
                    onBackground = androidx.compose.ui.graphics.Color.White
                )
            ) {
                AdSkipperApp(
                    selectedApps = selectedApps.value,
                    availableApps = availableApps.value,
                    selectedContent = selectedContent.value,
                    recognizedContent = recognizedContent.value,
                    isServiceRunning = isServiceRunning.value,
                    currentMediaType = currentMediaType.value,
                    isRecording = isRecording.value,
                    onAddApp = { selectedApps.value = selectedApps.value + it },
                    onRemoveApp = { selectedApps.value = selectedApps.value - it },
                    onAddContent = { saveTargetText(it) },
                    onRemoveContent = { selectedContent.value = selectedContent.value - it },
                    onMediaTypeChange = { handleMediaTypeChange(it) },
                    onStartService = { startSkipperService() },
                    onStopService = { stopSkipperService() },
                    onStartRecording = { startRecording() },
                    onStopRecording = { stopRecording() }
                )
            }
        }
    }

    private fun setupTouchListener() {
        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            if (isRecording.value) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "Touch event recorded: x=${event.x}, y=${event.y}")
                        gestureRecorder.recordTap(event.x, event.y)
                    }
                }
                true
            } else false
        }
    }

    private fun handleMediaTypeChange(type: String) {
        Log.d(TAG, "Media type changed to: $type")
        currentMediaType.value = type
        if (type == "תמונה" && checkStoragePermission()) {
            pickImage.launch("image/*")
        }
    }

    private fun saveTargetText(text: String) {
        Log.d(TAG, "Saving target text: $text")
        selectedContent.value = selectedContent.value + text
        prefs.edit().apply {
            selectedApps.value.forEach { app ->
                putString("${app.packageName}_text", text)
            }
            apply()
        }
    }

    private fun checkStoragePermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestStoragePermission() {
        Log.d(TAG, "Requesting storage permission")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    private fun checkRequiredPermissions() {
        Log.d(TAG, "Checking required permissions")
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Missing permissions: $missingPermissions")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d(TAG, "All required permissions granted")
            initializeApp()
        }
    }

    private fun initializeApp() {
        Log.d(TAG, "Initializing app")
        if (!isAccessibilityServiceEnabled()) {
            Log.d(TAG, "Accessibility service not enabled")
            openAccessibilitySettings()
        }
    }

    private fun loadInstalledApps() {
        Log.d(TAG, "Loading installed apps")
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        availableApps.value = packageManager.queryIntentActivities(intent, 0)
            .map {
                AppInfo(
                    name = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .filterNot { isSystemApp(packageManager.getApplicationInfo(it.packageName, 0)) }
            .sortedBy { it.name }

        Log.d(TAG, "Loaded ${availableApps.value.size} apps")
    }

    private fun isSystemApp(appInfo: ApplicationInfo) =
        appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun startSkipperService() {
        Log.d(TAG, "Starting skipper service")
        when {
            selectedApps.value.isEmpty() -> {
                Log.d(TAG, "No apps selected")
                Toast.makeText(this, "נא לבחור לפחות אפליקציה אחת", Toast.LENGTH_SHORT).show()
            }
            selectedContent.value.isEmpty() -> {
                Log.d(TAG, "No content selected")
                Toast.makeText(this, "נא להזין לפחות תוכן אחד", Toast.LENGTH_SHORT).show()
            }
            !isAccessibilityServiceEnabled() -> {
                Log.d(TAG, "Accessibility service not enabled")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "אנא הפעל את שירות הנגישות של AdSkipper", Toast.LENGTH_LONG).show()
            }
            else -> {
                saveServicePreferences()
                startScreenCapture.launch(projectionManager.createScreenCaptureIntent())
                isServiceRunning.value = true
                Log.d(TAG, "Skipper service started")
                Toast.makeText(this, "שירות הדילוג הופעל", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTextDetectionService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, TextDetectionService::class.java).apply {
            action = TextDetectionService.ACTION_START
            putExtra(TextDetectionService.EXTRA_RESULT_CODE, resultCode)
            putExtra(TextDetectionService.EXTRA_DATA, data)
            putExtra(TextDetectionService.EXTRA_TARGET_TEXT, selectedContent.value.firstOrNull() ?: "")
            putExtra(TextDetectionService.EXTRA_TARGET_PACKAGE, selectedApps.value.firstOrNull()?.packageName ?: "")
        }
        startForegroundService(serviceIntent)
    }

    private fun saveServicePreferences() {
        Log.d(TAG, "Saving service preferences")
        prefs.edit().apply {
            putStringSet("selected_apps", selectedApps.value.map { it.packageName }.toSet())
            selectedApps.value.forEach { app ->
                selectedContent.value.forEach { content ->
                    putString("${app.packageName}_text", content)
                }
            }
            apply()
        }
    }

    private fun stopSkipperService() {
        Log.d(TAG, "Stopping skipper service")
        stopService(Intent(this, SkipperService::class.java))
        stopService(Intent(this, TextDetectionService::class.java))
        isServiceRunning.value = false
        Toast.makeText(this, "שירות הדילוג הופסק", Toast.LENGTH_SHORT).show()
    }

    private fun startRecording() {
        Log.d(TAG, "Starting gesture recording")
        gestureRecorder.startRecording()
        isRecording.value = true
        Toast.makeText(this, "מקליט פעולות...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        Log.d(TAG, "Stopping gesture recording")
        gestureRecorder.stopRecording()
        isRecording.value = false
        recordedActions = gestureRecorder.getRecordedActions()
        getSharedPreferences("gestures", MODE_PRIVATE).edit().apply {
            putString("recorded_actions", recordedActions.toString())
            apply()
        }
        Toast.makeText(this, "הקלטת הפעולות הסתיימה", Toast.LENGTH_SHORT).show()
    }

    private fun saveSelectedImage(uri: Uri) {
        Log.d(TAG, "Saving selected image: $uri")
        try {
            val image = InputImage.fromFilePath(this, uri)
            if (image.width < 100 || image.height < 100) {
                Log.d(TAG, "Image too small: ${image.width}x${image.height}")
                Toast.makeText(this, "התמונה קטנה מדי. נא לבחור תמונה גדולה יותר", Toast.LENGTH_SHORT).show()
                return
            }

            when (currentMediaType.value) {
                "תמונה" -> labelImage(image)
                "טקסט" -> recognizeText(image)
            }

            selectedApps.value.forEach { app ->
                contentResolver.openInputStream(uri)?.use { input ->
                    File(getExternalFilesDir(null), "${app.packageName}_target.jpg")
                        .outputStream().use { output ->
                            input.copyTo(output)
                        }
                }
            }
            Toast.makeText(this, "התמונה נשמרה בהצלחה", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}", e)
            Toast.makeText(this, "שגיאה בשמירת התמונה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeText(image: InputImage) {
        Log.d(TAG, "Starting text recognition")
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim().replace("\n", " ").replace("\\s+".toRegex(), " ")
                Log.d(TAG, "Text recognized: $text")
                selectedContent.value = selectedContent.value + text
                recognizedContent.value = text
                saveTargetText(text)
                if (recordedActions.isNotEmpty()) {
                    gesturePlayer.playActions(recordedActions)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
                Toast.makeText(this, "שגיאה בזיהוי טקסט", Toast.LENGTH_SHORT).show()
            }
    }

    private fun labelImage(image: InputImage) {
        Log.d(TAG, "Starting image labeling")
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { labels ->
                val labelText = labels.joinToString("\n") {
                    "${it.text} (${(it.confidence * 100).toInt()}%)"
                }
                Log.d(TAG, "Image labeled: $labelText")
                selectedContent.value = selectedContent.value + labelText
                saveTargetText(labelText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Image labeling failed", e)
                Toast.makeText(this, "שגיאה בזיהוי התמונה", Toast.LENGTH_SHORT).show()
            }
    }
}