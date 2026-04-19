package org.foss.gird

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var showHistory by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: App Management
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ListItem(
                    headlineContent = { Text("Power Mode") },
                    supportingContent = { Text(pollingMode) },
                    leadingContent = { Icon(Icons.Default.BatteryChargingFull, null) },
                    trailingContent = {
                        TextButton(onClick = {
                            val modes = listOf("Battery Saver", "Balanced", "High Precision")
                            val next = (modes.indexOf(pollingMode) + 1) % modes.size
                            pollingMode = modes[next]
                            GeofenceRepository.savePollingMode(context, pollingMode)
                        }) {
                            Text("Change")
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("System Notifications") },
                    leadingContent = { Icon(Icons.Outlined.Notifications, null) },
                    trailingContent = {
                        IconButton(onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                )
            }
        }

        // Section: Activity Log (Collapsible)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Activity Log") },
                    supportingContent = { Text("${history.size} events recorded") },
                    leadingContent = { Icon(Icons.Outlined.History, null) },
                    trailingContent = {
                        IconButton(onClick = { showHistory = !showHistory }) {
                            Icon(if (showHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                )
                
                AnimatedVisibility(visible = showHistory) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (history.isEmpty()) {
                            Text("No recent activity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            history.take(10).forEach { event ->
                                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(if (event.eventType == GeofenceState.INSIDE) Color.Green else Color.Gray, MaterialTheme.shapes.small))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(event.fenceName, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(if (event.eventType == GeofenceState.INSIDE) "In" else "Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (history.size > 10) {
                                TextButton(onClick = { /* Could open full history */ }) {
                                    Text("View full history")
                                }
                            }
                        }
                        
                        TextButton(
                            onClick = { GeofenceRepository.clearHistory(context) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Log")
                        }
                    }
                }
            }
        }

        // Section: Danger Zone
        Text("Maintenance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            ListItem(
                headlineContent = { Text("Reset Gird", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Wipe all locations, tasks, and history.") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                trailingContent = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.padding(8.dp)
            )
        }

        // About
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Gird v1.1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))
                context.startActivity(intent)
            }) {
                Text("Open Source License", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Complete Reset?") },
            text = { Text("This will permanently delete all your location contexts and tasks. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        GeofenceRepository.clearHistory(context)
                        // Wipe all
                        geofences.toList().forEach { GeofenceRepository.removeGeofence(context, it) }
                        GeofenceRepository.tasks.toList().forEach { GeofenceRepository.removeTask(context, it) }
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
