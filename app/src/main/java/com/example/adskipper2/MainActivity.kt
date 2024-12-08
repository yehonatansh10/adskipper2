package com.example.adskipper2

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.content.Context

data class AppInfo(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var gestureRecorder: GestureRecorder
    private lateinit var gesturePlayer: GesturePlayer
    private val handler = Handler(Looper.getMainLooper())
    private var lastDetectionTime = 0L
    private val detectionInterval = 1000L
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
        )
        private val TARGET_TEXTS = setOf(
            "דלג על מודעה",
            "דלגו על המודעה",
            "Skip Ad",
            "Skip ad",
            "Skip Ads",
            "sponsored"
        )
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
            checkRequiredPermissions() // נסה שוב לבקש הרשאות
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            Log.d(TAG, "Image selected: $uri")
            if (checkStoragePermission()) saveSelectedImage(it)
            else requestStoragePermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "App started")
            initializeComponents()
            setupUI()
            setupTouchListener()

            // בדיקת הרשאות בהפעלה
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            }

            if (!isAccessibilityServiceEnabled()) {
                requestAccessibilityPermission()
            }

            checkServiceStatus()
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
        loadInstalledApps()
        initializePrefs()
    }

    private fun initializePrefs() {
        Log.d(TAG, "Initializing preferences")
        if (!prefs.contains("selected_apps")) {
            prefs.edit().apply {
                putStringSet("selected_apps", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"))
                // הוספת מילת ברירת מחדל
                putString("com.zhiliaoapp.musically_target_sponsored", "sponsored")
                putString("com.ss.android.ugc.trill_target_sponsored", "sponsored")
                apply()
            }
        }
        loadSavedPreferences()
        loadSavedTargetWords()
    }

    private fun loadSavedPreferences() {
        val savedApps = prefs.getStringSet("selected_apps", setOf()) ?: setOf()
        val savedAppInfos = savedApps.mapNotNull { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                AppInfo(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = packageName
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading app info for $packageName", e)
                null
            }
        }.toSet()
        selectedApps.value = savedAppInfos

        val savedContent = savedApps.flatMap { packageName ->
            prefs.getString("${packageName}_text", null)?.let { setOf(it) } ?: setOf()
        }.toSet()
        selectedContent.value = savedContent
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

    private fun startSkipperService() {
        Log.d(TAG, "Starting skipper service")

        if (!Settings.canDrawOverlays(this) || !isAccessibilityServiceEnabled()) {
            Log.d(TAG, "Missing permissions, requesting...")
            checkAllPermissions()
            return
        }

        try {
            val serviceIntent = Intent(this, UnifiedSkipperService::class.java)
            if (!isServiceRunning(UnifiedSkipperService::class.java)) {
                startService(serviceIntent)
                Log.d(TAG, "Service started")
            } else {
                Log.d(TAG, "Service already running")
            }
            isServiceRunning.value = true
            saveServicePreferences()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
            Toast.makeText(this, "שגיאה בהפעלת השירות", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAllPermissions(): Boolean {
        // בדיקה אם יש הרשאת SYSTEM_ALERT_WINDOW
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return false
        }

        // בדיקה אם שירות הנגישות מופעל
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermission()
            return false
        }

        return true
    }

    private fun requestAccessibilityPermission() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(
                this,
                "אנא הפעל את שירות הנגישות של AdSkipper",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
            Toast.makeText(
                this,
                "שגיאה בפתיחת הגדרות נגישות",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopSkipperService() {
        try {
            stopService(Intent(this, UnifiedSkipperService::class.java))
            isServiceRunning.value = false
            Toast.makeText(this, "שירות הדילוג הופסק", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}", e)
        }
    }

    private fun checkServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        Log.d(TAG, "Accessibility service enabled: $enabled")
        if (enabled) {
            isServiceRunning.value = isServiceRunning(UnifiedSkipperService::class.java)
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if service is running", e)
        }
        return false
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(
                this,
                "אנא אשר הרשאת תצוגה מעל אפליקציות אחרות וחזור לאפליקציה",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open overlay settings", e)
            Toast.makeText(this, "שגיאה בפתיחת הגדרות תצוגה", Toast.LENGTH_LONG).show()
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

        // שמירת המילה עבור כל אפליקציה נבחרת
        prefs.edit().apply {
            selectedApps.value.forEach { app ->
                // שמירת המילה עם מזהה ייחודי
                putString("${app.packageName}_target_${text.hashCode()}", text)
            }
            apply()
        }

        // רענון השירות
        if (isServiceRunning.value) {
            stopSkipperService()
            startSkipperService()
        }
    }

    private fun loadSavedTargetWords() {
        val words = mutableSetOf<String>()
        selectedApps.value.forEach { app ->
            prefs.all.entries
                .filter { it.key.startsWith("${app.packageName}_target_") }
                .mapNotNullTo(words) { it.value as? String }
        }
        selectedContent.value = words
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

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val serviceName = "$packageName/${UnifiedSkipperService::class.java.name}"
        return enabledServices?.contains(serviceName) == true
    }

    private fun initializeApp() {
        Log.d(TAG, "Initializing app")
        if (!isAccessibilityServiceEnabled()) {
            Log.d(TAG, "Accessibility service not enabled")
            requestAccessibilityPermission()
        }
    }

    private fun loadInstalledApps() {
        Log.d(TAG, "Loading installed apps")
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        availableApps.value = try {
            packageManager.queryIntentActivities(intent, 0)
                .map {
                    AppInfo(
                        name = it.loadLabel(packageManager).toString(),
                        packageName = it.activityInfo.packageName
                    )
                }
                .distinctBy { it.packageName }
                .filterNot {
                    try {
                        isSystemApp(packageManager.getApplicationInfo(it.packageName, 0))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking system app status for ${it.packageName}", e)
                        true
                    }
                }
                .sortedBy { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading installed apps", e)
            emptyList()
        }

        Log.d(TAG, "Loaded ${availableApps.value.size} apps")
    }

    private fun isSystemApp(appInfo: ApplicationInfo) =
        appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun saveServicePreferences() {
        Log.d(TAG, "Saving service preferences")
        prefs.edit().apply {
            putStringSet("selected_apps", selectedApps.value.map { it.packageName }.toSet())
            selectedApps.value.forEach { app ->
                TARGET_TEXTS.forEach { text ->
                    putString("${app.packageName}_text", text)
                }
            }
            apply()
        }
        Log.d(TAG, "Service preferences saved successfully")
    }

    private fun startRecording() {
        Log.d(TAG, "Starting gesture recording request")
        try {
            if (isServiceRunning.value) {
                gestureRecorder.startRecording()
                isRecording.value = true
                Log.d(TAG, "Recording started successfully")
                Toast.makeText(this, "מקליט פעולות...", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Cannot start recording - service is not running")
                Toast.makeText(this, "אנא הפעל את השירות לפני תחילת ההקלטה", Toast.LENGTH_LONG).show()
                startSkipperService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            Toast.makeText(this, "שגיאה בהפעלת ההקלטה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "Stopping gesture recording")
        try {
            gestureRecorder.stopRecording()
            isRecording.value = false
            recordedActions = gestureRecorder.getRecordedActions()

            if (recordedActions.isNotEmpty()) {
                getSharedPreferences("gestures", MODE_PRIVATE).edit().apply {
                    putString("recorded_actions", recordedActions.toString())
                    apply()
                }
                Log.d(TAG, "Saved recorded actions: $recordedActions")
                Toast.makeText(this, "הקלטת הפעולות הסתיימה", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "No actions were recorded")
                Toast.makeText(this, "לא הוקלטו פעולות", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            Toast.makeText(this, "שגיאה בשמירת ההקלטה", Toast.LENGTH_SHORT).show()
        }
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
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        File(getExternalFilesDir(null), "${app.packageName}_target.jpg")
                            .outputStream().use { output ->
                                input.copyTo(output)
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving image for ${app.packageName}: ${e.message}", e)
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
        textRecognizer.process(image)
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