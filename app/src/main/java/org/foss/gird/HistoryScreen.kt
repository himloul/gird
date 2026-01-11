package org.foss.gird

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val history = GeofenceRepository.history
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { GeofenceRepository.clearHistory(context) }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear History")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No activity recorded yet.", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { event ->
                    HistoryItem(event)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(event: GeofenceEvent) {
    val isArrival = event.eventType == GeofenceState.INSIDE
    val dateString = DateFormat.format("MMM dd, HH:mm:ss", Date(event.timestamp)).toString()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isArrival) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = {
                Text(
                    text = if (isArrival) "Arrived at ${event.fenceName}" else "Left ${event.fenceName}",
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = { Text(dateString) },
            leadingContent = {
                val icon = if (isArrival) Icons.Default.NotificationsActive 
                           else Icons.Default.NotificationsNone
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isArrival) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
        )
    }
}