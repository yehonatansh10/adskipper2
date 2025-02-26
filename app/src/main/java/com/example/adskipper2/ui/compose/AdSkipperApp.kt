package com.example.adskipper2.ui.compose

import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adskipper2.AppInfo
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.ui.res.stringResource
import com.example.adskipper2.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdSkipperApp(
    selectedApps: Set<AppInfo>,
    availableApps: List<AppInfo>,
    selectedContent: Set<String>,
    recognizedContent: String,
    isServiceRunning: Boolean,
    currentMediaType: String,
    isRecording: Boolean,
    onAddApp: (AppInfo) -> Unit,
    onRemoveApp: (AppInfo) -> Unit,
    onAddContent: (String) -> Unit,
    onRemoveContent: (String) -> Unit,
    onMediaTypeChange: (String) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfService: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // כותרת
            Text(
                text = "AdSkipper",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 80.dp)
            )

            Spacer(modifier = Modifier.height(150.dp))

            // Custom Toggle Switch
            Box(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .size(128.dp, 64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
                    .clickable {
                        if (isServiceRunning) onStopService() else onStartService()
                    }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .align(if (isServiceRunning) Alignment.CenterEnd else Alignment.CenterStart)
                        .animateContentSize()
                )
            }

            // סטטוס שירות
            Text(
                text = if (isServiceRunning) "On" else "Off",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // כפתור סטטיסטיקות - חדש
            Button(
                onClick = onOpenStats,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF333333)
                ),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Statistics",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Statistics")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onOpenPrivacyPolicy,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.LightGray
                )
            ) {
                Text("Privacy Policy", fontSize = 12.sp)
            }

            TextButton(
                onClick = onOpenTermsOfService,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.LightGray
                )
            ) {
                Text("Terms of Use", fontSize = 12.sp)
            }
        }
    }
}