package com.yonash.adskipper2.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yonash.adskipper2.R

@Composable
fun LegalAgreementDialog(
    isHebrew: Boolean = true,
    onDismiss: () -> Unit,
    onAgree: () -> Unit,
    onViewPrivacyPolicy: () -> Unit,
    onViewTermsOfService: () -> Unit
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
                        stringResource(R.string.legal_agreement_title)
                    } else {
                        "Legal Agreement"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isHebrew) {
                        stringResource(R.string.legal_agreement_message)
                    } else {
                        "To use the app, you must agree to our Terms of Service and Privacy Policy."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // כפתורים לקריאת המסמכים
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onViewPrivacyPolicy) {
                        Text(
                            text = if (isHebrew) {
                                stringResource(R.string.review_privacy)
                            } else {
                                "Read Privacy Policy"
                            }
                        )
                    }

                    TextButton(onClick = onViewTermsOfService) {
                        Text(
                            text = if (isHebrew) {
                                stringResource(R.string.review_terms)
                            } else {
                                "Read Terms of Service"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // כפתורי הפעולה
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (isHebrew) {
                                "ביטול"
                            } else {
                                "Cancel"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = onAgree) {
                        Text(
                            text = if (isHebrew) {
                                stringResource(R.string.i_agree)
                            } else {
                                "I Agree"
                            }
                        )
                    }
                }
            }
        }
    }
}