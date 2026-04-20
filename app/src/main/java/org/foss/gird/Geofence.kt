package org.foss.gird

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofences")
data class Geofence(
    @PrimaryKey val id: String,
    val name: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusInMeters: Float,
    val isActive: Boolean = true,
    val lastState: GeofenceState = GeofenceState.UNKNOWN,
    val color: GeofenceColor = GeofenceColor.GREEN
)

enum class GeofenceState {
    INSIDE, OUTSIDE, UNKNOWN
}

enum class GeofenceColor {
    RED, BLUE, GREEN
}
