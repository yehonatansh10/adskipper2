package com.example.adskipper2

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Path
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
import androidx.activity.ComponentActivity.MODE_PRIVATE
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.adskipper2.ui.compose.AdSkipperApp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import android.content.SharedPreferences


sealed class GestureAction {
    data class Tap(val x: Float, val y: Float) : GestureAction()
    data class DoubleTap(val x: Float, val y: Float) : GestureAction()
    data class Scroll(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : GestureAction()
}

class GestureRecorder {
    var isRecording = false
        private set

    private val actions = mutableListOf<GestureAction>()
    private var startTime: Long = 0

    fun startRecording() {
        isRecording = true
        actions.clear()
        startTime = System.currentTimeMillis()
    }

    fun stopRecording() {
        isRecording = false
    }

    fun recordTap(x: Float, y: Float) {
        if (isRecording) {
            actions.add(GestureAction.Tap(x, y))
        }
    }

    fun getRecordedActions(): List<GestureAction> = actions.toList()
}

class GesturePlayer(private val context: Context) {
    private val gestureBuilder = GestureDescription.Builder()
    private var accessibilityService: AccessibilityService? = null
    private val handler = Handler(Looper.getMainLooper())

    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityService = service
    }

    fun playActions(actions: List<GestureAction>) {
        actions.forEachIndexed { index, action ->
            handler.postDelayed({
                when (action) {
                    is GestureAction.Tap -> performTap(action.x, action.y)
                    is GestureAction.DoubleTap -> performDoubleTap(action.x, action.y)
                    is GestureAction.Scroll -> performScroll(action.startX, action.startY, action.endX, action.endY)
                }
            }, index * 500L)
        }
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        accessibilityService?.dispatchGesture(gesture, null, null)
    }

    private fun performDoubleTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .addStroke(GestureDescription.StrokeDescription(path, 200, 100))
            .build()

        accessibilityService?.dispatchGesture(gesture, null, null)
    }

    private fun performScroll(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        accessibilityService?.dispatchGesture(gesture, null, null)
    }
}

data class AppInfo(
    val name: String,
    val packageName: String
)

class MainActivity : ComponentActivity() {
    companion object {
        const val STORAGE_PERMISSION_REQUEST_CODE = 100
    }

    private val selectedApps = mutableStateOf(setOf<AppInfo>())
    private val selectedContent = mutableStateOf(setOf<String>())
    private val recognizedContent = mutableStateOf("")
    private var isServiceRunning = mutableStateOf(false)
    private var currentMediaType = mutableStateOf("טקסט")
    private var availableApps = mutableStateOf(listOf<AppInfo>())

    private lateinit var gestureRecorder: GestureRecorder
    private lateinit var gesturePlayer: GesturePlayer
    private var recordedActions: List<GestureAction> = emptyList()
    private var isRecording = mutableStateOf(false)
    private lateinit var prefs: SharedPreferences

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (checkStoragePermission()) {
                saveSelectedImage(it)
            } else {
                requestStoragePermission()
            }
        }
    }

    init {
        prefs = getSharedPreferences("targets", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putStringSet("selected_apps", setOf("com.google.android.youtube"))
        editor.putString("com.google.android.youtube_text", "דלג על מודעה")
        editor.apply()
    }

    private fun checkStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    private fun checkRequiredPermissions() {
        if (!checkStoragePermission()) {
            requestStoragePermission()
        }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }

    private fun saveTargetText(text: String) {
        getSharedPreferences("targets", MODE_PRIVATE).edit().apply {
            selectedApps.value.forEach { app ->
                putString("${app.packageName}_text", text)
            }
            apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadInstalledApps()
        checkRequiredPermissions()

        gestureRecorder = GestureRecorder()
        gesturePlayer = GesturePlayer(this)

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
                    selectedApps = selectedApps.value,  // העבר את ה-Set<AppInfo> ישירות
                    availableApps = availableApps.value,
                    selectedContent = selectedContent.value,
                    recognizedContent = recognizedContent.value,
                    isServiceRunning = isServiceRunning.value,
                    currentMediaType = currentMediaType.value,
                    isRecording = isRecording.value,
                    onAddApp = { app -> selectedApps.value = selectedApps.value + app },
                    onRemoveApp = { app -> selectedApps.value = selectedApps.value - app },
                    onAddContent = { content ->
                        selectedContent.value = selectedContent.value + content
                        saveTargetText(content)
                    },
                    onRemoveContent = { content ->
                        selectedContent.value = selectedContent.value - content
                    },
                    onMediaTypeChange = { type ->
                        currentMediaType.value = type
                        if (type == "תמונה" && checkStoragePermission()) {
                            pickImage.launch("image/*")
                        }
                    },
                    onStartService = { startSkipperService() },
                    onStopService = { stopSkipperService() },
                    onStartRecording = { startRecording() },
                    onStopRecording = { stopRecording() }
                )
            }
        }

        findViewById<View>(android.R.id.content).setOnTouchListener { view, event ->
            if (isRecording.value) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureRecorder.recordTap(event.x, event.y)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // רישום גלילה אם נדרש
                    }
                    MotionEvent.ACTION_UP -> {
                        // סיום הפעולה
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun startSkipperService() {
        if (selectedApps.value.isEmpty()) {
            Toast.makeText(this, "נא לבחור לפחות אפליקציה אחת", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedContent.value.isEmpty()) {
            Toast.makeText(this, "נא להזין לפחות תוכן אחד", Toast.LENGTH_SHORT).show()
            return
        }

        getSharedPreferences("targets", MODE_PRIVATE).edit().apply {
            putStringSet("selected_apps", selectedApps.value.map { it.packageName }.toSet())
            selectedApps.value.forEach { app ->
                selectedContent.value.forEach { content ->
                    putString("${app.packageName}_text", content)
                }
            }
            apply()
        }

        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "אנא הפעל את שירות הנגישות של AdSkipper", Toast.LENGTH_LONG).show()
        } else {
            startService(Intent(this, SkipperService::class.java))
            Toast.makeText(this, "שירות הדילוג הופעל", Toast.LENGTH_SHORT).show()
        }

        isServiceRunning.value = true
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
        Toast.makeText(this, "הקלטת הפעולות הסתיימה", Toast.LENGTH_SHORT).show()

        getSharedPreferences("gestures", MODE_PRIVATE).edit().apply {
            putString("recorded_actions", recordedActions.toString())
            apply()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )

        if (accessibilityEnabled == 1) {
            val serviceString = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            serviceString?.let {
                return it.contains("${packageName}/${packageName}.SkipperService")
            }
        }
        return false
    }

    private fun stopSkipperService() {
        stopService(Intent(this, SkipperService::class.java))
        isServiceRunning.value = false
        Toast.makeText(this, "שירות הדילוג הופסק", Toast.LENGTH_SHORT).show()
    }

    private fun loadInstalledApps() {
        val packageManager = packageManager
        availableApps.value = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { !isSystemApp(it) }
            .map {
                AppInfo(
                    name = packageManager.getApplicationLabel(it).toString(),
                    packageName = it.packageName
                )
            }
            .sortedBy { it.name }
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
                val inputStream = contentResolver.openInputStream(uri)
                val file = File(getExternalFilesDir(null), "${app.packageName}_target.jpg")

                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Toast.makeText(this, "התמונה נשמרה בהצלחה", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ImageProcessing", "Error: ${e.message}", e)
            Toast.makeText(this, "שגיאה בשמירת התמונה", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun recognizeText(image: InputImage) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                    .replace("\n", " ")
                    .replace("\\s+".toRegex(), " ")

                Log.d("TextRecognition", "Recognized text: $text")

                selectedContent.value = selectedContent.value + text
                recognizedContent.value = text
                saveTargetText(text)

                if (recordedActions.isNotEmpty()) {
                    gesturePlayer.playActions(recordedActions)
                }
            }
            .addOnFailureListener { e ->
                Log.e("TextRec", "Text recognition failed", e)
            }
    }
    private fun labelImage(image: InputImage) {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        labeler.process(image)
            .addOnSuccessListener { labels ->
                val sb = StringBuilder()
                for (label in labels) {
                    sb.append("${label.text} (${(label.confidence * 100).toInt()}%)\n")
                }
                selectedContent.value = selectedContent.value + sb.toString()
                saveTargetText(sb.toString())
            }
            .addOnFailureListener { e ->
                Log.e("ImageLabeling", "Error: ${e.message}", e)
                Toast.makeText(this, "שגיאה בזיהוי התמונה: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}