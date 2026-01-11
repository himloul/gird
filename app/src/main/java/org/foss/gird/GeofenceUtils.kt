package org.foss.gird

import kotlin.math.*

object GeofenceUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the distance between two points using the Haversine formula.
     * Returns distance in meters.
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destLat = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(originLat) * cos(destLat)
        val c = 2 * asin(sqrt(a))
        return EARTH_RADIUS_METERS * c
    }
}
