package org.foss.gird

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val fenceId: String? = null, // Optional now: Can be a global task
    val content: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val startTime: Int? = null,
    val endTime: Int? = null,
    val completedAt: Long? = null,
    val isRecurring: Boolean = false // New: !daily or frequent tasks
)
