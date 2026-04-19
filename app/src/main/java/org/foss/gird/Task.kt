package org.foss.gird

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val fenceId: String,
    val content: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val dueDate: Long? = null, // Optional deadline or reminder time
    val startTime: Int? = null, // Start hour (0-23) for time-windowing
    val endTime: Int? = null    // End hour (0-23) for time-windowing
)
