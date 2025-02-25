package com.example.adskipper2.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.adskipper2.R
import com.example.adskipper2.UnifiedSkipperService
import com.example.adskipper2.util.Logger
import java.util.concurrent.atomic.AtomicBoolean

class PermissionManager(private val activity: Activity) {
    companion object {
        private const val TAG = "PermissionManager"
        private const val PERMISSION_CHECK_INTERVAL = 2000L // 2 seconds
    }

    private val requiredPermissions = mutableListOf<String>().apply {
        add(Manifest.permission.FOREGROUND_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private val permissionCheckRunning = AtomicBoolean(false)

    fun setupPermissionLauncher(onPermissionsResult: (Boolean) -> Unit) {
        permissionLauncher = (activity as androidx.activity.ComponentActivity)
            .registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allGranted = permissions.all { it.value }
                onPermissionsResult(allGranted)
            }
    }

    fun checkAndRequestPermissions(onPermissionsGranted: () -> Unit) {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        when {
            missingPermissions.isEmpty() -> {
                // בדיקה נוספת להרשאות מיוחדות
                if (areSpecialPermissionsGranted()) {
                    onPermissionsGranted()
                } else {
                    requestSpecialPermissions()
                }
            }

            missingPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } -> {
                // יש להסביר למשתמש מדוע ההרשאות נחוצות
                showPermissionRationaleDialog(missingPermissions.toTypedArray())
            }

            else -> {
                // בקשת הרשאות
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }

    private fun areSpecialPermissionsGranted(): Boolean {
        val canDrawOverlays = Settings.canDrawOverlays(activity)
        val accessibilityEnabled = isAccessibilityServiceEnabled(activity)
        return canDrawOverlays && accessibilityEnabled
    }

    private fun requestSpecialPermissions() {
        if (!Settings.canDrawOverlays(activity)) {
            requestOverlayPermission { /* do nothing here */ }
        } else if (!isAccessibilityServiceEnabled(activity)) {
            requestAccessibilityPermission()
        }
    }

    fun startPermissionVerification(onPermissionsChanged: (Boolean) -> Unit) {
        if (permissionCheckRunning.compareAndSet(false, true)) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    val allGranted = areAllPermissionsGranted()
                    onPermissionsChanged(allGranted)
                    if (permissionCheckRunning.get()) {
                        handler.postDelayed(this, PERMISSION_CHECK_INTERVAL)
                    }
                }
            }
            handler.post(runnable)
        }
    }

    fun stopPermissionVerification() {
        permissionCheckRunning.set(false)
    }

    fun areAllPermissionsGranted(): Boolean {
        // בדיקה של הרשאות רגילות
        val regularPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }

        // בדיקה של הרשאות מיוחדות
        return regularPermissionsGranted && areSpecialPermissionsGranted()
    }


    private fun showPermissionRationaleDialog(permissions: Array<String>) {
        val message = buildRationaleMessage(permissions)

        AlertDialog.Builder(activity)
            .setTitle(R.string.permissions_required)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                permissionLauncher.launch(permissions)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun buildRationaleMessage(permissions: Array<String>): String {
        val sb = StringBuilder()
        sb.append(activity.getString(R.string.permission_rationale_intro))

        permissions.forEach { permission ->
            sb.append("\n\n• ")
            when (permission) {
                Manifest.permission.FOREGROUND_SERVICE -> {
                    sb.append(activity.getString(R.string.permission_rationale_foreground_service))
                }
                Manifest.permission.POST_NOTIFICATIONS -> {
                    sb.append(activity.getString(R.string.permission_rationale_notifications))
                }
                // הוסף הסברים נוספים לפי הצורך
            }
        }

        return sb.toString()
    }

    fun requestOverlayPermission(onPermissionGranted: () -> Unit) {
        if (Settings.canDrawOverlays(activity)) {
            onPermissionGranted()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.overlay_permission_required)
            .setMessage(R.string.overlay_permission_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun requestAccessibilityPermission() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.accessibility_permission_required)
            .setMessage(R.string.accessibility_permission_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        val serviceName = "${context.packageName}/${UnifiedSkipperService::class.java.name}"
        return enabledServices?.contains(serviceName) == true
    }
}