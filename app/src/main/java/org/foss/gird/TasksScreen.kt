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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(onNavigateToMap: () -> Unit) {
    val context = LocalContext.current
    val tasks = GeofenceRepository.tasks
    val geofences = GeofenceRepository.geofences
    
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
                // 1. PINNED: Active Context
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

                // 2. Proximity List
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

                if (tasks.isEmpty() && activeFence == null) {
                    item {
                        Box(modifier = Modifier.fillParentMaxHeight(0.6f), contentAlignment = Alignment.Center) {
                            Text("No tasks active.", color = MaterialTheme.colorScheme.outlineVariant)
                        }
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
                TextButton(
                    onClick = {
                        GeofenceRepository.removeTask(context, taskToDelete!!)
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
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

@Composable
fun ActiveLocationHub(fence: Geofence, tasks: List<Task>, onDeleteRequest: (Task) -> Unit, onEditRequest: (Task) -> Unit) {
    val context = LocalContext.current
    val pendingTasks = tasks.filter { !it.isCompleted }
    var showQuickAdd by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(fence.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (pendingTasks.isEmpty()) {
                Text("Zero tasks here. Enjoy your stay!", style = MaterialTheme.typography.bodyMedium)
            } else {
                pendingTasks.forEach { task ->
                    TaskRowMinimal(
                        task = task, 
                        onToggle = { GeofenceRepository.toggleTaskCompletion(context, task.id, it) },
                        onDelete = { onDeleteRequest(task) },
                        onEdit = { onEditRequest(task) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            if (!showQuickAdd) {
                Button(
                    onClick = { showQuickAdd = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Quick Add")
                }
            } else {
                QuickAddRow(onAdd = { content, dueDate, start, end ->
                    GeofenceRepository.addTask(context, Task(
                        fenceId = fence.id, 
                        content = content,
                        dueDate = dueDate,
                        startTime = start,
                        endTime = end
                    ))
                    showQuickAdd = false
                }, onCancel = { showQuickAdd = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationContextCard(fence: Geofence, tasks: List<Task>, onDeleteRequest: (Task) -> Unit, onEditRequest: (Task) -> Unit) {
    val context = LocalContext.current
    val pendingCount = tasks.count { !it.isCompleted }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(
                        when(fence.color) {
                            GeofenceColor.RED -> Color(0xFFD32F2F)
                            GeofenceColor.BLUE -> Color(0xFF1976D2)
                            GeofenceColor.GREEN -> Color(0xFF2E7D32)
                        }, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(fence.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                if (pendingCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Text(
                            pendingCount.toString(), 
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                var showAddTaskDialog by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (tasks.isEmpty()) {
                        Text("No tasks saved for this location.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        tasks.forEach { task ->
                            TaskRowMinimal(
                                task = task, 
                                onToggle = { GeofenceRepository.toggleTaskCompletion(context, task.id, it) },
                                onDelete = { onDeleteRequest(task) },
                                onEdit = { onEditRequest(task) }
                            )
                        }
                    }
                    
                    TextButton(
                        onClick = { showAddTaskDialog = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Task")
                    }
                }

                if (showAddTaskDialog) {
                    TaskFormDialog(
                        title = "New Task",
                        fenceName = fence.name,
                        onDismiss = { showAddTaskDialog = false },
                        onConfirm = { content, dueDate, start, end ->
                            GeofenceRepository.addTask(context, Task(
                                fenceId = fence.id, 
                                content = content,
                                dueDate = dueDate,
                                startTime = start,
                                endTime = end
                            ))
                            showAddTaskDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskRowMinimal(task: Task, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = onToggle,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).selectable(selected = false, onClick = onEdit)
        ) {
            Text(
                text = task.content,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )
            if (task.dueDate != null || task.startTime != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime, 
                        null, 
                        modifier = Modifier.size(12.dp), 
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val timeStr = buildString {
                        if (task.dueDate != null) {
                            append(android.text.format.DateFormat.format("MMM dd", java.util.Date(task.dueDate)))
                        }
                        if (task.startTime != null && task.endTime != null) {
                            if (isNotEmpty()) append(", ")
                            append("${task.startTime}:00 - ${task.endTime}:00")
                        }
                    }
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.DeleteOutline, 
                contentDescription = "Delete", 
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddRow(onAdd: (String, Long?, Int?, Int?) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var showTimeOptions by remember { mutableStateOf(false) }
    
    // Basic Time Window presets
    var selectedWindow by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("New task...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )
            IconButton(onClick = { showTimeOptions = !showTimeOptions }) {
                Icon(
                    Icons.Default.AccessTime, 
                    null, 
                    tint = if (selectedWindow != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = { if (text.isNotBlank()) onAdd(text, null, selectedWindow?.first, selectedWindow?.second) }) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
        
        AnimatedVisibility(visible = showTimeOptions) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val windows = listOf(
                    "Work (9-17)" to (9 to 17),
                    "Evening (18-22)" to (18 to 22),
                    "Morning (6-9)" to (6 to 9)
                )
                windows.forEach { (label, window) ->
                    FilterChip(
                        selected = selectedWindow == window,
                        onClick = { selectedWindow = if (selectedWindow == window) null else window },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(
    title: String,
    initialText: String = "",
    initialWindow: Pair<Int, Int>? = null,
    fenceName: String, 
    onDismiss: () -> Unit, 
    onConfirm: (String, Long?, Int?, Int?) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedWindow by remember { mutableStateOf<Pair<Int, Int>?>(initialWindow) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("For $fenceName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What needs to be done?") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Time Window (Optional)", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val windows = listOf(
                        "9 AM - 5 PM" to (9 to 17),
                        "6 PM - 10 PM" to (18 to 22),
                        "6 AM - 9 AM" to (6 to 9)
                    )
                    windows.forEach { (label, window) ->
                        FilterChip(
                            selected = selectedWindow == window,
                            onClick = { selectedWindow = if (selectedWindow == window) null else window },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text, null, selectedWindow?.first, selectedWindow?.second) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, actionText: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}
