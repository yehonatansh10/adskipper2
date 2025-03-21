package com.yonash.adskipper2.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yonash.adskipper2.R

@Composable
fun AccessibilityDisclosureDialog(
    isHebrew: Boolean = true,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isHebrew) {
                        stringResource(R.string.accessibility_disclosure_title)
                    } else {
                        "Accessibility Service Disclosure"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isHebrew) {
                        stringResource(R.string.accessibility_disclosure_content)
                    } else {
                        "AdSkipper needs access to Accessibility Services to detect ads on your screen and automatically skip them. This allows the app to:\n\n• See screen content to identify ad elements\n• Perform scrolling actions to skip past ads\n• Work automatically in the background\n\nAll data processing happens locally on your device. No screen content or personal information is collected, stored, or transmitted to any external servers."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel"
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = onAccept) {
                        Text(
                            text = if (isHebrew) {
                                stringResource(R.string.i_understand_and_agree)
                            } else {
                                "I Understand and Agree"
                            }
                        )
                    }
                }
            }
        }
    }
}