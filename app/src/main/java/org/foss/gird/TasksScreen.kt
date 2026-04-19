package org.foss.gird

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.util.*

// --- DATA MODELS ---

sealed class StoryItem(val time: Long) {
    class Arrival(time: Long, val location: String) : StoryItem(time)
    class Departure(time: Long, val location: String, val duration: String?) : StoryItem(time)
    class Achievement(time: Long, val taskContent: String) : StoryItem(time)
}

sealed class FeedItem {
    data class ActiveHeader(val locationName: String) : FeedItem()
    data class TaskItem(val task: Task, val isContextual: Boolean) : FeedItem()
    data class TimelineEvent(val item: StoryItem) : FeedItem()
    data class SectionHeader(val title: String) : FeedItem()
}

fun buildStoryline(history: List<GeofenceEvent>, tasks: List<Task>): List<StoryItem> {
    val items = mutableListOf<StoryItem>()
    history.forEach { event ->
        if (event.eventType == GeofenceState.INSIDE) items.add(StoryItem.Arrival(event.timestamp, event.fenceName))
        else {
            val arrival = history.find { it.fenceName == event.fenceName && it.eventType == GeofenceState.INSIDE && it.timestamp < event.timestamp }
            items.add(StoryItem.Departure(event.timestamp, event.fenceName, arrival?.let { val m = (event.timestamp - it.timestamp) / 60000; if (m < 60) "${m}m" else "${m/60}h ${m%60}m" }))
        }
    }
    tasks.filter { it.isCompleted && it.completedAt != null }.forEach { items.add(StoryItem.Achievement(it.completedAt!!, it.content)) }
    return items.sortedByDescending { it.time }.take(10)
}

// --- MAIN SCREEN ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TasksScreen(onNavigateToMap: () -> Unit) {
    val context = LocalContext.current
    val tasks = GeofenceRepository.tasks
    val geofences = GeofenceRepository.geofences
    val history = GeofenceRepository.history
    
    val activeFence by remember { derivedStateOf { geofences.find { it.lastState == GeofenceState.INSIDE } } }
    
    val feedItems by remember {
        derivedStateOf {
            val items = mutableListOf<FeedItem>()
            val pendingTasks = tasks.filter { !it.isCompleted }
            
            val contextualTasks = pendingTasks.filter { it.fenceId == activeFence?.id }
            if (contextualTasks.isNotEmpty() && activeFence != null) {
                items.add(FeedItem.ActiveHeader(activeFence!!.name))
                contextualTasks.forEach { items.add(FeedItem.TaskItem(it, true)) }
            }

            val otherTasks = pendingTasks.filter { it.fenceId != activeFence?.id }
            if (otherTasks.isNotEmpty()) {
                items.add(FeedItem.SectionHeader("Queue"))
                otherTasks.forEach { items.add(FeedItem.TaskItem(it, false)) }
            }

            val storyline = buildStoryline(history, tasks)
            if (storyline.isNotEmpty()) {
                items.add(FeedItem.SectionHeader("Narrative"))
                storyline.forEach { items.add(FeedItem.TimelineEvent(it)) }
            }
            items
        }
    }

    // --- Sequential Input State ---
    var step by remember { mutableIntStateOf(1) } 
    var poiQuery by remember { mutableStateOf("") }
    var selectedFence by remember { mutableStateOf<Geofence?>(null) }
    var taskText by remember { mutableStateOf("") }

    LaunchedEffect(activeFence) {
        if (step == 1 && activeFence != null) {
            selectedFence = activeFence
            poiQuery = activeFence?.name ?: ""
            step = 2
        }
    }

    val poiSuggestions = remember(poiQuery, geofences) {
        if (poiQuery.isEmpty()) geofences 
        else geofences.filter { it.name.contains(poiQuery, ignoreCase = true) }
    }

    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() with fadeOut() }
                ) { targetStep ->
                    if (targetStep == 1) {
                        Column {
                            Text("Where?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = poiQuery,
                                onValueChange = { poiQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search location or 'Inbox'...") },
                                leadingIcon = { Icon(Icons.Default.Place, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                            )
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                poiSuggestions.take(3).forEach { fence ->
                                    ListItem(
                                        headlineContent = { Text(fence.name) },
                                        leadingContent = { Icon(Icons.Default.Place, null, tint = if (fence.id == activeFence?.id) Color.Green else MaterialTheme.colorScheme.outline) },
                                        modifier = Modifier.clickable { selectedFence = fence; poiQuery = fence.name; step = 2 }.height(48.dp),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                                ListItem(
                                    headlineContent = { Text("Inbox (No location)") },
                                    leadingContent = { Icon(Icons.Default.Inbox, null) },
                                    modifier = Modifier.clickable { selectedFence = null; poiQuery = ""; step = 2 }.height(48.dp),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { step = 1 },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Place, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = selectedFence?.name ?: "Inbox", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("(Tap to change)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = taskText,
                                onValueChange = { taskText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("What needs to be done?") },
                                singleLine = true,
                                trailingIcon = {
                                    if (taskText.isNotBlank()) {
                                        IconButton(onClick = {
                                            GeofenceRepository.addTask(context, Task(content = taskText, fenceId = selectedFence?.id))
                                            taskText = ""
                                        }) { Icon(Icons.Default.ArrowDownward, null, tint = MaterialTheme.colorScheme.primary) }
                                    }
                                },
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }

        if (geofences.isEmpty()) {
            EmptyState(Icons.Default.Map, "Your journey starts here.", "Open Map", onNavigateToMap)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(feedItems, key = { 
                    when(it) {
                        is FeedItem.ActiveHeader -> "header-${it.locationName}"
                        is FeedItem.SectionHeader -> "section-${it.title}"
                        is FeedItem.TaskItem -> "task-${it.task.id}"
                        is FeedItem.TimelineEvent -> "event-${it.item.time}"
                    }
                }) { item ->
                    when (item) {
                        is FeedItem.ActiveHeader -> Text("Now at ${item.locationName}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                        is FeedItem.SectionHeader -> Text(item.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 32.dp, bottom = 8.dp))
                        is FeedItem.TaskItem -> FlatTaskRow(item.task, item.isContextual, { GeofenceRepository.toggleTaskCompletion(context, item.task.id, it) }, { taskToDelete = item.task }, { taskToEdit = item.task })
                        is FeedItem.TimelineEvent -> FlatStorylineNode(item.item)
                    }
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }

    if (taskToDelete != null) {
        AlertDialog(onDismissRequest = { taskToDelete = null }, title = { Text("Delete?") }, text = { Text("Remove task?") }, confirmButton = { TextButton(onClick = { GeofenceRepository.removeTask(context, taskToDelete!!); taskToDelete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Cancel") } })
    }
    if (taskToEdit != null) {
        val fence = geofences.find { it.id == taskToEdit?.fenceId }
        TaskFormDialog("Edit Task", taskToEdit?.content ?: "", fence?.name ?: "Inbox", { taskToEdit = null }, { c ->
            GeofenceRepository.updateTask(context, taskToEdit!!.copy(content = c))
            taskToEdit = null
        })
    }
}

// --- COMPONENTS ---

@Composable
fun FlatTaskRow(task: Task, isContextual: Boolean, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val geofences = GeofenceRepository.geofences
    val location = geofences.find { it.id == task.fenceId }
    Row(modifier = Modifier.fillMaxWidth().clickable { onEdit() }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.isCompleted, onCheckedChange = onToggle, colors = CheckboxDefaults.colors(checkmarkColor = MaterialTheme.colorScheme.background))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = task.content, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isContextual) FontWeight.Bold else FontWeight.Normal, color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
            if (location != null && !isContextual) Text(location.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.outlineVariant) }
    }
}

@Composable
fun FlatStorylineNode(item: StoryItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            val timeStr = android.text.format.DateFormat.format("HH:mm", Date(item.time)).toString()
            Text(text = when(item) { is StoryItem.Arrival -> "Entered ${item.location}"; is StoryItem.Departure -> "Left ${item.location}"; is StoryItem.Achievement -> "Finished ${item.taskContent}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(title: String, initialText: String, fenceName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var t by remember { mutableStateOf(initialText) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(
            value = t, 
            onValueChange = { t = it }, 
            modifier = Modifier.fillMaxWidth(), 
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent, 
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }, confirmButton = { Button(onClick = { if (t.isNotBlank()) onConfirm(t) }) { Text("Save") } })
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, actionText: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp)); Text(message, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp)); TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}
