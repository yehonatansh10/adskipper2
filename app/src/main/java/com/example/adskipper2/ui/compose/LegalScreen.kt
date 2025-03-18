package com.example.adskipper2.ui.compose

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.adskipper2.R
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    initialDocumentType: LegalDocumentType,
    onBackClick: () -> Unit
) {
    var documentType by remember { mutableStateOf(initialDocumentType) }

    // האם המסמך בעברית
    val isHebrew = documentType == LegalDocumentType.PRIVACY_POLICY_HEBREW ||
            documentType == LegalDocumentType.TERMS_OF_SERVICE_HEBREW ||
            documentType == LegalDocumentType.SUPPORT_HEBREW

    // כותרת המסמך
    val title = when (documentType) {
        LegalDocumentType.PRIVACY_POLICY_HEBREW,
        LegalDocumentType.PRIVACY_POLICY_ENGLISH ->
            if (isHebrew) stringResource(R.string.privacy_policy) else "Privacy Policy"

        LegalDocumentType.TERMS_OF_SERVICE_HEBREW,
        LegalDocumentType.TERMS_OF_SERVICE_ENGLISH ->
            if (isHebrew) stringResource(R.string.terms_of_service) else "Terms of Service"

        LegalDocumentType.SUPPORT_HEBREW,
        LegalDocumentType.SUPPORT_ENGLISH ->
            if (isHebrew) stringResource(R.string.support) else "Support"
    }

    // החלפת שפה
    fun toggleLanguage() {
        documentType = when (documentType) {
            LegalDocumentType.PRIVACY_POLICY_HEBREW -> LegalDocumentType.PRIVACY_POLICY_ENGLISH
            LegalDocumentType.PRIVACY_POLICY_ENGLISH -> LegalDocumentType.PRIVACY_POLICY_HEBREW
            LegalDocumentType.TERMS_OF_SERVICE_HEBREW -> LegalDocumentType.TERMS_OF_SERVICE_ENGLISH
            LegalDocumentType.TERMS_OF_SERVICE_ENGLISH -> LegalDocumentType.TERMS_OF_SERVICE_HEBREW
            LegalDocumentType.SUPPORT_HEBREW -> LegalDocumentType.SUPPORT_ENGLISH
            LegalDocumentType.SUPPORT_ENGLISH -> LegalDocumentType.SUPPORT_HEBREW
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזרה")
                    }
                },
                actions = {
                    // כפתור החלפת שפה
                    IconButton(onClick = { toggleLanguage() }) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = if (isHebrew) "Switch to English" else "עבור לעברית",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.White)
            ) {
                // תצוגת המסמך באמצעות WebView
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = object : WebViewClient() {
                                // מניעת פתיחת דפים חיצוניים
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    return url?.startsWith("file:///android_asset/") != true
                                }

                                // מניעת גישה לתוכן חיצוני
                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.cancel()
                                }
                            }

                            settings.apply {
                                javaScriptEnabled = false  // כבר מוגדר אצלך, זה מצוין
                                allowFileAccess = false     // מניעת גישה לקבצים אחרים
                                allowContentAccess = false  // מניעת גישה לספקי תוכן
                                allowFileAccessFromFileURLs = false  // מניעת גישה מקובץ לקובץ
                                allowUniversalAccessFromFileURLs = false  // מניעת גישה אוניברסלית

                                setSupportMultipleWindows(false)  // מניעת פתיחת חלונות נוספים
                                javaScriptCanOpenWindowsAutomatically = false  // מניעת פתיחת חלונות אוטומטית

                                // הגבלת זיכרון מטמון
                                setCacheMode(WebSettings.LOAD_NO_CACHE)

                                // הגדרות נוספות לאבטחה
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = true
                                }
                            }

                            loadUrl("file:///android_asset/${documentType.fileName}")
                        }
                    },
                    update = { webView ->
                        webView.loadUrl("file:///android_asset/${documentType.fileName}")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}