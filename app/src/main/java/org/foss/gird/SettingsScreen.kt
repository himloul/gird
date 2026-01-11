package org.foss.gird

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val history = GeofenceRepository.history
    val geofences = GeofenceRepository.geofences
    BackHandler(onBack = onBack)

    var pollingMode by remember { mutableStateOf(GeofenceRepository.loadPollingMode(context)) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section: Tracking
            Text("Tracking Control", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Power Optimization") },
                supportingContent = { 
                    Text(when(pollingMode) {
                        "High Precision" -> "Highest frequency (30s-1m). High battery impact."
                        "Balanced" -> "Dynamic polling based on proximity. Recommended."
                        else -> "Low frequency (5m-10m). Best battery life."
                    })
                },
                leadingContent = { Icon(Icons.Default.BatteryChargingFull, null) },
                trailingContent = {
                    TextButton(onClick = {
                        val modes = listOf("Battery Saver", "Balanced", "High Precision")
                        val next = (modes.indexOf(pollingMode) + 1) % modes.size
                        pollingMode = modes[next]
                        GeofenceRepository.savePollingMode(context, pollingMode)
                    }) {
                        Text(pollingMode)
                    }
                }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: Statistics
            Text("Data Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Active Fences") },
                trailingContent = { Text(geofences.size.toString()) },
                leadingContent = { Icon(Icons.Default.Layers, null) }
            )
            ListItem(
                headlineContent = { Text("Events Logged") },
                trailingContent = { Text(history.size.toString()) },
                leadingContent = { Icon(Icons.Default.History, null) }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: Tools
            Text("Privacy & Maintenance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Notification Access") },
                supportingContent = { Text("Verify alert permissions") },
                leadingContent = { Icon(Icons.Default.Notifications, null) },
                trailingContent = {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Verify")
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Reset Application") },
                supportingContent = { Text("Wipe all fences and history") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset")
                    }
                }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: About
            Text("About Gird", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Software Version") },
                supportingContent = { Text("1.0.0 Stable") },
                leadingContent = { Icon(Icons.Default.Info, null) }
            )

            ListItem(
                headlineContent = { Text("Legal Mentions") },
                supportingContent = { Text("Open source licenses & attributions") },
                leadingContent = { Icon(Icons.Default.Gavel, null) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = { showLegalDialog = true }) {
                        Text("View")
                    }
                }
            )
            
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apache 2.0 Open Source License")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showLegalDialog) {
            AlertDialog(
                onDismissRequest = { showLegalDialog = false },
                title = { Text("Open Source Notices") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Gird is built with open-source software:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Android Open Source Project (Apache 2.0)", style = MaterialTheme.typography.labelSmall)
                        Text("• Jetpack Compose & AndroidX (Apache 2.0)", style = MaterialTheme.typography.labelSmall)
                        Text("• Osmdroid / OpenStreetMap (Apache 2.0)", style = MaterialTheme.typography.labelSmall)
                        Text("• Accompanist Permissions (Apache 2.0)", style = MaterialTheme.typography.labelSmall)
                        Text("• Kotlin Standard Library (Apache 2.0)", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("This app and its source code are licensed under the Apache License 2.0.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLegalDialog = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Data?") },
                text = { Text("This will permanently delete all your geofences and activity logs. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            GeofenceRepository.clearHistory(context)
                            // Clear all fences safely
                            val toRemove = geofences.toList()
                            toRemove.forEach { GeofenceRepository.removeGeofence(context, it) }
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Wipe Everything")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
