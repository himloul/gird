package org.foss.gird

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofence_events")
data class GeofenceEvent(
    @PrimaryKey val id: String,
    val fenceName: String,
    val eventType: GeofenceState, // INSIDE (Arrival) or OUTSIDE (Departure)
    val timestamp: Long = System.currentTimeMillis()
)
