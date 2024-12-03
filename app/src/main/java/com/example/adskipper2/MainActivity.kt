package com.example.adskipper2

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) initializeApp()
        else Toast.makeText(this, "נדרשות הרשאות להפעלת האפליקציה", Toast.LENGTH_LONG).show()
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (checkStoragePermission()) saveSelectedImage(it)
            else requestStoragePermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            initializeComponents()
            setupUI()
            setupTouchListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "שגיאה באתחול האפליקציה", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeComponents() {
        prefs = getSharedPreferences("targets", MODE_PRIVATE)
        gestureRecorder = GestureRecorder()
        gesturePlayer = GesturePlayer(this)
        loadInstalledApps()
        checkRequiredPermissions()
        initializePrefs()
    }

    private fun initializePrefs() {
        prefs.edit().apply {
            putStringSet("selected_apps", setOf("com.google.android.youtube"))
            putString("com.google.android.youtube_text", "דלג על מודעה")
            apply()
        }
    }

    private fun setupUI() {
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
                    MotionEvent.ACTION_DOWN -> gestureRecorder.recordTap(event.x, event.y)
                }
                true
            } else false
        }
    }

    private fun handleMediaTypeChange(type: String) {
        currentMediaType.value = type
        if (type == "תמונה" && checkStoragePermission()) {
            pickImage.launch("image/*")
        }
    }

    private fun saveTargetText(text: String) {
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
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    private fun checkRequiredPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeApp()
        }
    }

    private fun initializeApp() {
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings()
        }
    }

    private fun loadInstalledApps() {
        availableApps.value = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filterNot { isSystemApp(it) }
            .map { AppInfo(packageManager.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.name }
    }

    private fun isSystemApp(appInfo: ApplicationInfo) =
        appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun startSkipperService() {
        when {
            selectedApps.value.isEmpty() ->
                Toast.makeText(this, "נא לבחור לפחות אפליקציה אחת", Toast.LENGTH_SHORT).show()
            selectedContent.value.isEmpty() ->
                Toast.makeText(this, "נא להזין לפחות תוכן אחד", Toast.LENGTH_SHORT).show()
            !isAccessibilityServiceEnabled() -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "אנא הפעל את שירות הנגישות של AdSkipper", Toast.LENGTH_LONG).show()
            }
            else -> {
                saveServicePreferences()
                startService(Intent(this, SkipperService::class.java))
                isServiceRunning.value = true
                Toast.makeText(this, "שירות הדילוג הופעל", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveServicePreferences() {
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
        stopService(Intent(this, SkipperService::class.java))
        isServiceRunning.value = false
        Toast.makeText(this, "שירות הדילוג הופסק", Toast.LENGTH_SHORT).show()
    }

    private fun startRecording() {
        gestureRecorder.startRecording()
        isRecording.value = true
        Toast.makeText(this, "מקליט פעולות...", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
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
        try {
            val image = InputImage.fromFilePath(this, uri)
            if (image.width < 100 || image.height < 100) {
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
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim().replace("\n", " ").replace("\\s+".toRegex(), " ")
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
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { labels ->
                val labelText = labels.joinToString("\n") {
                    "${it.text} (${(it.confidence * 100).toInt()}%)"
                }
                selectedContent.value = selectedContent.value + labelText
                saveTargetText(labelText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Image labeling failed", e)
                Toast.makeText(this, "שגיאה בזיהוי התמונה", Toast.LENGTH_SHORT).show()
            }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityEnabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            if (accessibilityEnabled != 1) return false

            val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return settingValue?.contains("${packageName}/${SkipperService::class.java.canonicalName}") == true
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
            Toast.makeText(this, "לא ניתן לפתוח הגדרות נגישות", Toast.LENGTH_SHORT).show()
        }
    }
}