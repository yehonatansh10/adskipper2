package com.example.adskipper2

import android.Manifest
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
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.adskipper2.config.KeywordManager
import com.example.adskipper2.gesture.GestureAction
import com.example.adskipper2.gesture.GesturePlayer
import com.example.adskipper2.gesture.GestureRecorder
import com.example.adskipper2.service.ServiceManager
import com.example.adskipper2.service.ServiceState
import com.example.adskipper2.ui.compose.AdSkipperApp
import com.example.adskipper2.ui.compose.LegalAgreementDialog
import com.example.adskipper2.ui.compose.LegalDocumentType
import com.example.adskipper2.ui.compose.LegalScreen
import com.example.adskipper2.util.ErrorHandler
import com.example.adskipper2.util.InputValidator
import com.example.adskipper2.util.Logger
import com.example.adskipper2.analytics.AnalyticsManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import android.view.accessibility.AccessibilityManager
import android.content.Context
import com.example.adskipper2.storage.SecurePreferences
import androidx.appcompat.app.AlertDialog

data class AppInfo(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {
    private lateinit var securePrefs: SecurePreferences
    private lateinit var gestureRecorder: GestureRecorder
    private lateinit var gesturePlayer: GesturePlayer
    private lateinit var errorHandler: ErrorHandler
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var keywordManager: KeywordManager

    private var showLegalScreen = mutableStateOf(false)
    private var currentLegalDocument = mutableStateOf(LegalDocumentType.PRIVACY_POLICY_HEBREW)
    private var showLegalAgreementDialog = mutableStateOf(false)

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
            "Sponsored"
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            Logger.d(TAG, "All permissions granted")
            initializeApp()
        } else {
            Logger.e(TAG, "Permissions not granted: ${permissions.filterValues { !it }.keys}")
            Toast.makeText(this, "נדרשות הרשאות להפעלת האפליקציה", Toast.LENGTH_LONG).show()
            checkRequiredPermissions() // נסה שוב לבקש הרשאות
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            Logger.d(TAG, "Image selected: $uri")
            if (checkStoragePermission()) saveSelectedImage(it)
            else requestStoragePermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Logger.initialize(applicationContext)
            Logger.d(TAG, "App started")
            initializeComponents()
            val securePreferences = SecurePreferences(this)
            if (securePreferences.shouldShowEncryptionWarning()) {
                showEncryptionWarningDialog()
                securePreferences.markEncryptionWarningShown()
            }
            checkLegalAgreement()
            setupUI()
            setupTouchListener()

            // בדיקה וטעינה של מצב השירות
            checkServiceStatus()

        } catch (e: Exception) {
            Logger.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error initializing the application", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEncryptionWarningDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.encryption_warning_title)
            .setMessage(R.string.encryption_warning_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupPeriodicStatusCheck() {
        val handler = Handler(Looper.getMainLooper())
        val statusChecker = object : Runnable {
            override fun run() {
                // בדיקת מצב השירות
                checkServiceStatus()

                if (isServiceRunning.value) {
                    // אם השירות פעיל אבל אפליקציית המטרה לא מגיבה, הפעל מחדש
                    val isTargetAppResponding = checkTargetAppResponding()
                    if (!isTargetAppResponding) {
                        Logger.d(TAG, "Target app not responding, restarting service")
                        stopSkipperService()
                        handler.postDelayed({ startSkipperService() }, 1000)
                    }
                }

                handler.postDelayed(this, 60000) // בדיקה כל דקה
            }
        }

        handler.post(statusChecker)
    }

    private fun checkTargetAppResponding(): Boolean {
        // בדיקה פשוטה אם האפליקציה מגיבה
        // אפשר לנסות לקבל מידע מהשירות או לבדוק אם הייתה פעילות לאחרונה
        val lastActivityTime = serviceManager.getLastActivityTime()
        return System.currentTimeMillis() - lastActivityTime < 5 * 60 * 1000 // 5 דקות
    }

    // בדיקה אם המשתמש כבר הסכים לתנאים בעבר
    private fun checkLegalAgreement() {
        val prefs = getSharedPreferences("legal_prefs", MODE_PRIVATE)
        val hasAgreed = prefs.getBoolean("has_agreed_to_terms", false)

        if (!hasAgreed) {
            showLegalAgreementDialog.value = true
        }
    }

    // שמירת הסכמת המשתמש
    private fun saveLegalAgreement() {
        getSharedPreferences("legal_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("has_agreed_to_terms", true)
            .apply()

        showLegalAgreementDialog.value = false
    }

    private fun initializeComponents() {
        try {
            Logger.d(TAG, "Initializing components")
            errorHandler = ErrorHandler.getInstance(this)
            analyticsManager = AnalyticsManager.getInstance(this)
            keywordManager = KeywordManager.getInstance(this)
            securePrefs = SecurePreferences(this)
            gestureRecorder = GestureRecorder()
            gesturePlayer = GesturePlayer(this)
            serviceManager = ServiceManager.getInstance(this)

            loadInstalledApps()
            loadSavedPreferences()
        } catch (e: Exception) {
            Logger.e(TAG, "Error in initializeComponents", e)
            errorHandler.handleError(TAG, e)
        }
    }

    private fun loadSavedPreferences() {
        try {
            // קבלת הגדרות מה-KeywordManager
            val appConfigs = keywordManager.getAppConfigMap()

            // המרה לרשימת אפליקציות
            val appInfos = appConfigs.keys.mapNotNull { packageName ->
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    AppInfo(
                        name = packageManager.getApplicationLabel(appInfo).toString(),
                        packageName = packageName
                    )
                } catch (e: Exception) {
                    Logger.e(TAG, "Error loading app info for $packageName", e)
                    null
                }
            }.toSet()

            selectedApps.value = appInfos

            // קבלת כל מילות המפתח
            selectedContent.value = keywordManager.getAllKeywords()

        } catch (e: Exception) {
            Logger.e(TAG, "Error loading saved preferences", e)
        }
    }

    private fun setupUI() {
        Logger.d(TAG, "Setting up UI")
        val isHebrew = resources.configuration.locales[0].language == "he"

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color.White,
                    onPrimary = Color.Black,
                    surface = Color.Black,
                    onSurface = Color.White,
                    background = Color.Black,
                    onBackground = Color.White
                )
            ) {
                when {
                    showLegalScreen.value -> {
                        LegalScreen(
                            initialDocumentType = currentLegalDocument.value,
                            onBackClick = { showLegalScreen.value = false }
                        )
                    }
                    else -> {
                        AdSkipperApp(
                            selectedApps = selectedApps.value,
                            availableApps = availableApps.value,
                            selectedContent = selectedContent.value,
                            recognizedContent = recognizedContent.value,
                            isServiceRunning = isServiceRunning.value,
                            currentMediaType = currentMediaType.value,
                            isRecording = isRecording.value,
                            onAddApp = { onAppSelected(it) },
                            onRemoveApp = { selectedApps.value = selectedApps.value - it },
                            onAddContent = { saveTargetText(it) },
                            onRemoveContent = { selectedContent.value = selectedContent.value - it },
                            onMediaTypeChange = { handleMediaTypeChange(it) },
                            onStartService = { startSkipperService() },
                            onStopService = { stopSkipperService() },
                            onStartRecording = { startRecording() },
                            onStopRecording = { stopRecording() },
                            onOpenPrivacyPolicy = {
                                currentLegalDocument.value = LegalDocumentType.getPrivacyPolicy(isHebrew)
                                showLegalScreen.value = true
                            },
                            onOpenTermsOfService = {
                                currentLegalDocument.value = LegalDocumentType.getTermsOfService(isHebrew)
                                showLegalScreen.value = true
                            },
                            onOpenSupport = {
                                currentLegalDocument.value = LegalDocumentType.getSupport(isHebrew)
                                showLegalScreen.value = true
                            }
                        )

                        // הצגת דיאלוג הסכמה אם נדרש
                        if (showLegalAgreementDialog.value) {
                            LegalAgreementDialog(
                                isHebrew = isHebrew,
                                onDismiss = { finish() }, // סגירת האפליקציה אם המשתמש לא מסכים
                                onAgree = { saveLegalAgreement() },
                                onViewPrivacyPolicy = {
                                    currentLegalDocument.value = LegalDocumentType.getPrivacyPolicy(isHebrew)
                                    showLegalScreen.value = true
                                    showLegalAgreementDialog.value = false // להסתיר זמנית את הדיאלוג
                                },
                                onViewTermsOfService = {
                                    currentLegalDocument.value = LegalDocumentType.getTermsOfService(isHebrew)
                                    showLegalScreen.value = true
                                    showLegalAgreementDialog.value = false // להסתיר זמנית את הדיאלוג
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setupTouchListener() {
        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            if (isRecording.value) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Logger.d(TAG, "Touch event recorded: x=${event.x}, y=${event.y}")
                        gestureRecorder.recordTap(event.x, event.y)
                    }
                }
                true
            } else false
        }
    }

    private fun startSkipperService() {
        Logger.d(TAG, "Starting skipper service")

        if (!Settings.canDrawOverlays(this) || !isAccessibilityServiceEnabled()) {
            Logger.d(TAG, "Missing permissions, requesting...")

            // First check for overlay permission
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return
            }

            // Then check for accessibility permission
            if (!isAccessibilityServiceEnabled()) {
                requestAccessibilityPermission()
                return
            }

            return
        }

        try {
            // הפעל את השירות רק אם צריך
            if (!isServiceRunning(UnifiedSkipperService::class.java)) {
                val serviceIntent = Intent(this, UnifiedSkipperService::class.java)
                startService(serviceIntent)
            }

            // הגדר את מצב השירות כמאופשר
            ServiceState.getInstance(this).setEnabled(true)
            isServiceRunning.value = true

            Toast.makeText(this, "Skip service started successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            errorHandler.handleError(TAG, e)
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
            // מעבר ישיר להגדרות הנגישות ללא הצגת דיאלוג
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(
                this,
                getString(R.string.accessibility_permission_rationale),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open accessibility settings", e)
            Toast.makeText(this, R.string.settings_open_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSkipperService() {
        try {
            // הגדר את מצב השירות כמושבת
            ServiceState.getInstance(this).setEnabled(false)
            isServiceRunning.value = false

            Toast.makeText(this, "Skip service has been discontinued.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping service: ${e.message}", e)
        }
    }

    private fun checkServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        Logger.d(TAG, "Accessibility service enabled: $enabled")
        if (enabled) {
            isServiceRunning.value = ServiceState.getInstance(this).isEnabled()
        } else {
            isServiceRunning.value = false
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
            Logger.e(TAG, "Error checking if service is running", e)
        }
        return false
    }

    private fun requestOverlayPermission() {
        try {
            // מעבר ישיר להגדרות הרשאת מוצג מעל פריטים אחרים ללא הצגת דיאלוג
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)

            // הצגת הודעה קצרה למשתמש
            Toast.makeText(
                this,
                getString(R.string.overlay_permission_rationale),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open overlay settings", e)
            Toast.makeText(this, "Error opening display settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleMediaTypeChange(type: String) {
        Logger.d(TAG, "Media type changed to: $type")
        currentMediaType.value = type
        if (type == "תמונה" && checkStoragePermission()) {
            pickImage.launch("image/*")
        }
    }

    private fun saveSelectedImage(uri: Uri) {
        Logger.d(TAG, "Saving selected image: $uri")

        try {
            // אימות ה-URI לפני שמירה
            if (!InputValidator.validateContentUri(this, uri)) {
                Toast.makeText(this, "התמונה שנבחרה אינה תקינה", Toast.LENGTH_SHORT).show()
                return
            }

            val image = InputImage.fromFilePath(this, uri)
            if (image.width < 100 || image.height < 100) {
                Logger.d(TAG, "Image too small: ${image.width}x${image.height}")
                Toast.makeText(this, "התמונה קטנה מדי. נא לבחור תמונה גדולה יותר", Toast.LENGTH_SHORT).show()
                return
            }

            when (currentMediaType.value) {
                "טקסט" -> recognizeText(image)
            }

            selectedApps.value.forEach { app ->
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val outputFile = File(getExternalFilesDir(null), "${app.packageName}_target.jpg")
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error saving image for ${app.packageName}: ${e.message}", e)
                }
            }
            Toast.makeText(this, "התמונה נשמרה בהצלחה", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving image: ${e.message}", e)
            Toast.makeText(this, "שגיאה בשמירת התמונה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestStoragePermission() {
        Logger.d(TAG, "Requesting storage permission")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    private fun checkRequiredPermissions() {
        Logger.d(TAG, "Checking required permissions")
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            Logger.d(TAG, "Missing permissions: $missingPermissions")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Logger.d(TAG, "All required permissions granted")
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
        Logger.d(TAG, "Initializing app")
        if (!isAccessibilityServiceEnabled()) {
            Logger.d(TAG, "Accessibility service not enabled")
            requestAccessibilityPermission()
        }
    }

    private fun loadInstalledApps() {
        Logger.d(TAG, "Loading installed apps")
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
                        Logger.e(TAG, "Error checking system app status for ${it.packageName}", e)
                        true
                    }
                }
                .sortedBy { it.name }
        } catch (e: Exception) {
            Logger.e(TAG, "Error loading installed apps", e)
            emptyList()
        }

        Logger.d(TAG, "Loaded ${availableApps.value.size} apps")
    }

    private fun isSystemApp(appInfo: ApplicationInfo) =
        appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun startRecording() {
        Logger.d(TAG, "Starting gesture recording request")
        try {
            if (isServiceRunning.value) {
                gestureRecorder.startRecording()
                isRecording.value = true
                Logger.d(TAG, "Recording started successfully")
                Toast.makeText(this, "מקליט פעולות...", Toast.LENGTH_SHORT).show()
            } else {
                Logger.d(TAG, "Cannot start recording - service is not running")
                Toast.makeText(this, "אנא הפעל את השירות לפני תחילת ההקלטה", Toast.LENGTH_LONG).show()
                startSkipperService()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error starting recording: ${e.message}", e)
            Toast.makeText(this, "שגיאה בהפעלת ההקלטה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        Logger.d(TAG, "Stopping gesture recording")
        try {
            gestureRecorder.stopRecording()
            isRecording.value = false
            recordedActions = gestureRecorder.getRecordedActions()

            if (recordedActions.isNotEmpty()) {
                getSharedPreferences("gestures", MODE_PRIVATE).edit().apply {
                    putString("recorded_actions", recordedActions.toString())
                    apply()
                }
                Logger.d(TAG, "Saved recorded actions: $recordedActions")
                Toast.makeText(this, "הקלטת הפעולות הסתיימה", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "No actions were recorded")
                Toast.makeText(this, "לא הוקלטו פעולות", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping recording: ${e.message}", e)
            Toast.makeText(this, "שגיאה בשמירת ההקלטה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onAppSelected(app: AppInfo) {
        if (InputValidator.validatePackageName(this, app.packageName)) {
            selectedApps.value = selectedApps.value + app
        } else {
            Toast.makeText(this, "אפליקציה לא תקפה: ${app.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeText(image: InputImage) {
        Logger.d(TAG, "Starting text recognition")

        // הגבלת גודל וסוג התמונה למניעת דליפות משאבים
        if (image.width > 4000 || image.height > 4000) {
            Logger.e(TAG, "Image too large: ${image.width}x${image.height}")
            Toast.makeText(this, "התמונה גדולה מדי", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // שימוש ב-InputValidator לניקוי הטקסט
                    val recognizedText = visionText.text.trim()
                    val sanitizedText = InputValidator.validateText(recognizedText)

                    if (sanitizedText.isNotBlank()) {
                        Logger.d(TAG, "Text recognized: $sanitizedText")
                        selectedContent.value = selectedContent.value + sanitizedText
                        recognizedContent.value = sanitizedText
                        saveTargetText(sanitizedText)
                        if (recordedActions.isNotEmpty()) {
                            gesturePlayer.playActions(recordedActions)
                        }
                    } else {
                        Toast.makeText(this, "לא זוהה טקסט משמעותי", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Logger.e(TAG, "Text recognition failed", e)
                    Toast.makeText(this, "שגיאה בזיהוי טקסט", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Logger.e(TAG, "Critical error in text recognition", e)
            Toast.makeText(this, "שגיאה קריטית בזיהוי טקסט", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTargetText(text: String) {
        // אימות הטקסט לפני שמירה
        val validText = InputValidator.validateText(text)
        if (validText.isBlank()) {
            Logger.d(TAG, "Empty text after validation, ignoring")
            return
        }

        if (validText.length > 100) {
            Logger.d(TAG, "Text too long, truncating")
        }

        Logger.d(TAG, "Saving target text: ${Logger.sanitizeMessage(validText)}")

        // הוספת המילה לכל האפליקציות הנתמכות
        keywordManager.addKeywordToAllApps(validText)

        // עדכון המצב המקומי
        selectedContent.value = selectedContent.value + validText

        // רענון השירות רק אם הוא רץ כבר
        if (isServiceRunning.value) {
            stopSkipperService()
            startSkipperService()
        }
    }
}