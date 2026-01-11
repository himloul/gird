package org.foss.gird

data class GeofenceEvent(
    val id: String,
    val fenceName: String,
    val eventType: GeofenceState, // INSIDE (Arrival) or OUTSIDE (Departure)
    val timestamp: Long = System.currentTimeMillis()
)
