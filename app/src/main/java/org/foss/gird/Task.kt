package org.foss.gird

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val fenceId: String,
    val content: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val startTime: Int? = null,
    val endTime: Int? = null,
    val completedAt: Long? = null // New: Record when task was actually finished
)
