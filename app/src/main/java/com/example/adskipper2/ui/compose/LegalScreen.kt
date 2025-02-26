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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    initialDocumentType: LegalDocumentType,
    onBackClick: () -> Unit
) {
    var documentType by remember { mutableStateOf(initialDocumentType) }

    // האם המסמך בעברית
    val isHebrew = documentType == LegalDocumentType.PRIVACY_POLICY_HEBREW ||
            documentType == LegalDocumentType.TERMS_OF_SERVICE_HEBREW

    // כותרת המסמך
    val title = when (documentType) {
        LegalDocumentType.PRIVACY_POLICY_HEBREW,
        LegalDocumentType.PRIVACY_POLICY_ENGLISH ->
            if (isHebrew) stringResource(R.string.privacy_policy) else "Privacy Policy"

        LegalDocumentType.TERMS_OF_SERVICE_HEBREW,
        LegalDocumentType.TERMS_OF_SERVICE_ENGLISH ->
            if (isHebrew) stringResource(R.string.terms_of_service) else "Terms of Service"
    }

    // החלפת שפה
    fun toggleLanguage() {
        documentType = when (documentType) {
            LegalDocumentType.PRIVACY_POLICY_HEBREW -> LegalDocumentType.PRIVACY_POLICY_ENGLISH
            LegalDocumentType.PRIVACY_POLICY_ENGLISH -> LegalDocumentType.PRIVACY_POLICY_HEBREW
            LegalDocumentType.TERMS_OF_SERVICE_HEBREW -> LegalDocumentType.TERMS_OF_SERVICE_ENGLISH
            LegalDocumentType.TERMS_OF_SERVICE_ENGLISH -> LegalDocumentType.TERMS_OF_SERVICE_HEBREW
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
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = false
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