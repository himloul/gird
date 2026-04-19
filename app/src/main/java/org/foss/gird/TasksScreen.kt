package org.foss.gird

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(onNavigateToMap: () -> Unit) {
    val context = LocalContext.current
    val tasks = GeofenceRepository.tasks
    val geofences = GeofenceRepository.geofences
    val history = GeofenceRepository.history
    
    val activeFence = geofences.find { it.lastState == GeofenceState.INSIDE }
    val groupedTasks = tasks.groupBy { it.fenceId }

    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (geofences.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Map,
                message = "Define your key locations on the map to start organizing tasks.",
                actionText = "Open Map",
                onAction = onNavigateToMap
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeFence != null) {
                    item {
                        Text("Right Now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        ActiveLocationHub(
                            fence = activeFence,
                            tasks = groupedTasks[activeFence.id] ?: emptyList(),
                            onDeleteRequest = { taskToDelete = it },
                            onEditRequest = { taskToEdit = it }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nearby Contexts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }

                items(geofences.filter { it.id != activeFence?.id }) { fence ->
                    val fenceTasks = groupedTasks[fence.id] ?: emptyList()
                    LocationContextCard(
                        fence = fence,
                        tasks = fenceTasks,
                        onDeleteRequest = { taskToDelete = it },
                        onEditRequest = { taskToEdit = it }
                    )
                }
// 3. STORYLINE SECTION
item {
    Spacer(modifier = Modifier.height(24.dp))
    var showClearConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Today's Journey", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        if (history.isNotEmpty() || tasks.any { it.isCompleted }) {
            TextButton(
                onClick = { showClearConfirm = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text("Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Reset Journey?") },
            text = { Text("This will clear today's movement and achievements from the timeline. Your tasks will stay checked.") },
            confirmButton = {
                TextButton(onClick = {
                    GeofenceRepository.clearHistory(context)
                    showClearConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

                if (history.isEmpty() && tasks.none { it.isCompleted }) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Your story begins when you move.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    val storyline = buildStoryline(history, tasks)
                    items(storyline) { item ->
                        StorylineNode(item)
                    }
                }
                
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Task?") },
            text = { Text("Are you sure you want to delete \"${taskToDelete?.content}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    GeofenceRepository.removeTask(context, taskToDelete!!)
                    taskToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (taskToEdit != null) {
        val fence = geofences.find { it.id == taskToEdit?.fenceId }
        TaskFormDialog(
            title = "Edit Task",
            initialText = taskToEdit?.content ?: "",
            initialWindow = if (taskToEdit?.startTime != null) taskToEdit!!.startTime!! to taskToEdit!!.endTime!! else null,
            fenceName = fence?.name ?: "Unknown",
            onDismiss = { taskToEdit = null },
            onConfirm = { content, _, start, end ->
                GeofenceRepository.updateTask(context, taskToEdit!!.copy(
                    content = content,
                    startTime = start,
                    endTime = end
                ))
                taskToEdit = null
            }
        )
    }
}

sealed class StoryItem(val time: Long) {
    class Arrival(time: Long, val location: String) : StoryItem(time)
    class Departure(time: Long, val location: String, val duration: String?) : StoryItem(time)
    class Achievement(time: Long, val taskContent: String) : StoryItem(time)
}

fun buildStoryline(history: List<GeofenceEvent>, tasks: List<Task>): List<StoryItem> {
    val items = mutableListOf<StoryItem>()
    history.forEach { event ->
        if (event.eventType == GeofenceState.INSIDE) {
            items.add(StoryItem.Arrival(event.timestamp, event.fenceName))
        } else {
            val arrival = history.find { it.fenceName == event.fenceName && it.eventType == GeofenceState.INSIDE && it.timestamp < event.timestamp }
            val durationStr = arrival?.let {
                val diffMin = (event.timestamp - it.timestamp) / 60000
                if (diffMin < 60) "${diffMin}m" else "${diffMin/60}h ${diffMin%60}m"
            }
            items.add(StoryItem.Departure(event.timestamp, event.fenceName, durationStr))
        }
    }
    tasks.filter { it.isCompleted && it.completedAt != null }.forEach { task ->
        items.add(StoryItem.Achievement(task.completedAt!!, task.content))
    }
    return items.sortedByDescending { it.time }.take(20)
}

@Composable
fun StorylineNode(item: StoryItem) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
            Box(modifier = Modifier.size(8.dp).background(
                when(item) {
                    is StoryItem.Arrival -> MaterialTheme.colorScheme.primary
                    is StoryItem.Achievement -> Color(0xFF4CAF50)
                    is StoryItem.Departure -> MaterialTheme.colorScheme.outline
                }, CircleShape
            ))
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            val timeStr = android.text.format.DateFormat.format("HH:mm", Date(item.time)).toString()
            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            when(item) {
                is StoryItem.Arrival -> Text(text = "Arrived at ${item.location}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                is StoryItem.Departure -> Text(text = "Left ${item.location}" + (item.duration?.let { " (Stayed $it)" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                is StoryItem.Achievement -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.taskContent, style = MaterialTheme.typography.bodyMedium, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun ActiveLocationHub(fence: Geofence, tasks: List<Task>, onDeleteRequest: (Task) -> Unit, onEditRequest: (Task) -> Unit) {
    val context = LocalContext.current
    val pendingTasks = tasks.filter { !it.isCompleted }
    var showQuickAdd by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(fence.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            pendingTasks.forEach { task ->
                TaskRowMinimal(task, { GeofenceRepository.toggleTaskCompletion(context, task.id, it) }, { onDeleteRequest(task) }, { onEditRequest(task) })
            }
            if (pendingTasks.isEmpty()) Text("No tasks here.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            if (!showQuickAdd) {
                Button(onClick = { showQuickAdd = true }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Quick Add")
                }
            } else {
                QuickAddRow({ content, _, s, e ->
                    GeofenceRepository.addTask(context, Task(fenceId = fence.id, content = content, startTime = s, endTime = e))
                    showQuickAdd = false
                }, { showQuickAdd = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationContextCard(fence: Geofence, tasks: List<Task>, onDeleteRequest: (Task) -> Unit, onEditRequest: (Task) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(when(fence.color) {
                        GeofenceColor.RED -> Color(0xFFD32F2F)
                        GeofenceColor.BLUE -> Color(0xFF1976D2)
                        else -> Color(0xFF2E7D32)
                    }, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp)); Text(fence.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                val count = tasks.count { !it.isCompleted }
                if (count > 0) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                    Text(count.toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            AnimatedVisibility(visible = expanded) {
                var showAddTaskDialog by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    tasks.forEach { task ->
                        TaskRowMinimal(task, { GeofenceRepository.toggleTaskCompletion(context, task.id, it) }, { onDeleteRequest(task) }, { onEditRequest(task) })
                    }
                    TextButton(onClick = { showAddTaskDialog = true }, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Add Task")
                    }
                }
                if (showAddTaskDialog) {
                    TaskFormDialog("New Task", "", null, fence.name, { showAddTaskDialog = false }, { content, _, s, e ->
                        GeofenceRepository.addTask(context, Task(fenceId = fence.id, content = content, startTime = s, endTime = e))
                        showAddTaskDialog = false
                    })
                }
            }
        }
    }
}

@Composable
fun TaskRowMinimal(task: Task, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.isCompleted, onCheckedChange = onToggle, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).selectable(selected = false, onClick = onEdit)) {
            Text(text = task.content, style = MaterialTheme.typography.bodyLarge, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null, color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            if (task.dueDate != null || task.startTime != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.width(4.dp))
                    val timeStr = buildString {
                        if (task.dueDate != null) append(android.text.format.DateFormat.format("MMM dd", Date(task.dueDate)))
                        if (task.startTime != null) { if (isNotEmpty()) append(", "); append("${task.startTime}:00 - ${task.endTime}:00") }
                    }
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddRow(onAdd: (String, Long?, Int?, Int?) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var showTimeOptions by remember { mutableStateOf(false) }
    var selectedWindow by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("New task...") }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), singleLine = true)
            IconButton(onClick = { showTimeOptions = !showTimeOptions }) { Icon(Icons.Default.AccessTime, null, tint = if (selectedWindow != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
            IconButton(onClick = { if (text.isNotBlank()) onAdd(text, null, selectedWindow?.first, selectedWindow?.second) }) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.outline) }
        }
        AnimatedVisibility(visible = showTimeOptions) {
            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val windows = listOf("Work (9-17)" to (9 to 17), "Evening (18-22)" to (18 to 22), "Morning (6-9)" to (6 to 9))
                windows.forEach { (label, window) -> FilterChip(selected = selectedWindow == window, onClick = { selectedWindow = if (selectedWindow == window) null else window }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(title: String, initialText: String, initialWindow: Pair<Int, Int>?, fenceName: String, onDismiss: () -> Unit, onConfirm: (String, Long?, Int?, Int?) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    var selectedWindow by remember { mutableStateOf<Pair<Int, Int>?>(initialWindow) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            Text("For $fenceName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("What needs to be done?") }, singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Time Window", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                val windows = listOf("9 AM - 5 PM" to (9 to 17), "6 PM - 10 PM" to (18 to 22), "6 AM - 9 AM" to (6 to 9))
                windows.forEach { (label, window) -> FilterChip(selected = selectedWindow == window, onClick = { selectedWindow = if (selectedWindow == window) null else window }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp)) }
            }
        }
    }, confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text, null, selectedWindow?.first, selectedWindow?.second) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, actionText: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}
