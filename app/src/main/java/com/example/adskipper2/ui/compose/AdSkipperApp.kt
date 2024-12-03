package com.example.adskipper2.ui.compose

import androidx.compose.foundation.background
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
    onStopRecording: () -> Unit
) {
    var showAppDropdown by remember { mutableStateOf(false) }
    var contentInputText by remember { mutableStateOf("") }

    val darkColors = darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        surface = Color.Black,
        onSurface = Color.White,
        background = Color.Black,
        onBackground = Color.White
    )

    MaterialTheme(
        colorScheme = darkColors
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "adskipper",
                fontSize = 34.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Text(
                "בחר אפליקציות",
                color = Color.White,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            ExposedDropdownMenuBox(
                expanded = showAppDropdown,
                onExpandedChange = { showAppDropdown = it },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(bottom = 8.dp)
            ) {
                TextField(
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("בחר אפליקציה", color = Color.Gray) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedContainerColor = Color(0xFF1A1A1A),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedIndicatorColor = Color.White
                    )
                )

                ExposedDropdownMenu(
                    expanded = showAppDropdown,
                    onDismissRequest = { showAppDropdown = false },
                    modifier = Modifier.background(Color(0xFF1A1A1A))
                ) {
                    availableApps.forEach { app ->
                        DropdownMenuItem(
                            text = { Text(app.name, color = Color.White) },
                            onClick = {
                                onAddApp(app)
                                showAppDropdown = false
                            }
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                items(selectedApps.toList()) { app ->
                    AssistChip(
                        onClick = { },
                        label = { Text(app.name, color = Color.White) },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveApp(app) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1A1A1A)
                        )
                    )
                }
            }

            Text(
                "סוג מדיה",
                color = Color.White,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMediaType == "תמונה",
                        onClick = { onMediaTypeChange("תמונה") },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = Color.Gray
                        )
                    )
                    Text("תמונה", color = Color.White)
                }
                Spacer(modifier = Modifier.width(32.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMediaType == "טקסט",
                        onClick = { onMediaTypeChange("טקסט") },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = Color.Gray
                        )
                    )
                    Text("טקסט", color = Color.White)
                }
            }

            Text(
                "הזן תוכן",
                color = Color.White,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            TextField(
                value = contentInputText,
                onValueChange = { contentInputText = it },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(bottom = 8.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    cursorColor = Color.White,
                    unfocusedIndicatorColor = Color.White,
                    focusedIndicatorColor = Color.White
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (contentInputText.isNotEmpty()) {
                                onAddContent(contentInputText)
                                contentInputText = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Content",
                            tint = Color.White
                        )
                    }
                }
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                items(selectedContent.toList()) { content ->
                    AssistChip(
                        onClick = { },
                        label = { Text(content, color = Color.White) },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveContent(content) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1A1A1A)
                        )
                    )
                }
            }

            // כפתור הקלטת פעולות
            Button(
                onClick = if (isRecording) onStopRecording else onStartRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else Color(0xFF1A1A1A),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(vertical = 8.dp)
            ) {
                Text(if (isRecording) "עצור הקלטת פעולות" else "הקלט פעולות")
            }

            if (recognizedContent.isNotEmpty()) {
                Text(
                    "תוכן שזוהה:",
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = recognizedContent,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(vertical = 8.dp)
                        .background(Color(0xFF1A1A1A))
                        .padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = if (isServiceRunning) "סטטוס: פעיל" else "סטטוס: לא פעיל",
                color = Color.White,
                modifier = Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartService,
                    enabled = !isServiceRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("הפעל שירות")
                }
                Button(
                    onClick = onStopService,
                    enabled = isServiceRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("עצור שירות")
                }
            }
        }
    }
}