package com.yonash.adskipper2.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    usageStats: Map<String, Any>,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("סטטיסטיקות שימוש") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזרה")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StatsCard(
                    title = "סטטיסטיקות כלליות",
                    items = listOf(
                        "ימים פעילים" to "${usageStats["days_active"] ?: 0}",
                        "הפעלות סה״כ" to "${usageStats["total_launches"] ?: 0}",
                        "פרסומות שזוהו" to "${usageStats["ads_detected"] ?: 0}",
                        "שגיאות" to "${usageStats["errors_count"] ?: 0}"
                    )
                )

                // אפליקציות מובילות
                @Suppress("UNCHECKED_CAST")
                val topApps = usageStats["top_apps"] as? Map<String, Int> ?: emptyMap()
                if (topApps.isNotEmpty()) {
                    StatsCard(
                        title = "אפליקציות מובילות",
                        items = topApps.entries.map {
                            val packageName = it.key
                            val appName = packageName.substringAfterLast('.')
                            appName to it.value.toString()
                        }
                    )
                }

                Text(
                    text = "* הנתונים נשמרים במכשירך בלבד ואינם נשלחים לשרת חיצוני",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 32.dp)
                )
            }
        }
    )
}

@Composable
fun StatsCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (label != items.last().first) {
                    Divider(
                        color = Color(0xFF333333),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}