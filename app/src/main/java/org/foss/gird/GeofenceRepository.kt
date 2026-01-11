package org.foss.gird

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

import android.util.Log

object GeofenceRepository {
    private const val TAG = "GeofenceRepository"
    private val _geofences = mutableStateListOf<Geofence>()
    val geofences: List<Geofence> get() = _geofences

    private const val PREFS_NAME = "geoapp_prefs"
    private const val KEY_GEOFENCES = "geofences_json"
    private const val KEY_HISTORY = "history_json"
    private val _history = mutableStateListOf<GeofenceEvent>()
    val history: List<GeofenceEvent> get() = _history

    fun addEvent(context: Context, event: GeofenceEvent) {
        _history.add(0, event) // Add to top
        if (_history.size > 100) _history.removeLast() // Keep last 100
        save(context)
    }

    fun clearHistory(context: Context) {
        _history.clear()
        save(context)
    }

    private const val KEY_MAP_LAT = "map_lat"
    private const val KEY_MAP_LON = "map_lon"
    private const val KEY_MAP_ZOOM = "map_zoom"
    private const val KEY_POLLING_MODE = "polling_mode"

    fun savePollingMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_POLLING_MODE, mode)
            .apply()
    }

    fun loadPollingMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_POLLING_MODE, "Balanced") ?: "Balanced"
    }

    fun saveMapState(context: Context, lat: Double, lon: Double, zoom: Double) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_MAP_LAT, java.lang.Double.doubleToRawLongBits(lat))
                .putLong(KEY_MAP_LON, java.lang.Double.doubleToRawLongBits(lon))
                .putLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(zoom))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving map state", e)
        }
    }

    fun loadMapState(context: Context): Triple<Double, Double, Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_LAT, java.lang.Double.doubleToRawLongBits(40.7128)))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_LON, java.lang.Double.doubleToRawLongBits(-74.0060)))
        val zoom = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(12.0)))
        return Triple(lat, lon, zoom)
    }

    fun load(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Load Fences
            val fenceJson = prefs.getString(KEY_GEOFENCES, null)
            if (fenceJson != null) {
                _geofences.clear()
                val jsonArray = JSONArray(fenceJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    _geofences.add(Geofence(
                        id = obj.getString("id"),
                        name = obj.optString("name", ""),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        radiusInMeters = obj.getDouble("radius").toFloat(),
                        isActive = obj.optBoolean("isActive", true),
                        lastState = GeofenceState.valueOf(obj.optString("lastState", "UNKNOWN")),
                        color = GeofenceColor.valueOf(obj.optString("color", "GREEN"))
                    ))
                }
            }

            // Load History
            val historyJson = prefs.getString(KEY_HISTORY, null)
            if (historyJson != null) {
                _history.clear()
                val jsonArray = JSONArray(historyJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    _history.add(GeofenceEvent(
                        id = obj.getString("id"),
                        fenceName = obj.getString("name"),
                        eventType = GeofenceState.valueOf(obj.getString("type")),
                        timestamp = obj.getLong("time")
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading data", e)
        }
    }

    fun save(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Save Fences
            val fenceArray = JSONArray()
            _geofences.forEach { fence ->
                fenceArray.put(JSONObject().apply {
                    put("id", fence.id)
                    put("name", fence.name)
                    put("latitude", fence.latitude)
                    put("longitude", fence.longitude)
                    put("radius", fence.radiusInMeters)
                    put("isActive", fence.isActive)
                    put("lastState", fence.lastState.name)
                    put("color", fence.color.name)
                })
            }

            // Save History
            val historyArray = JSONArray()
            _history.forEach { event ->
                historyArray.put(JSONObject().apply {
                    put("id", event.id)
                    put("name", event.fenceName)
                    put("type", event.eventType.name)
                    put("time", event.timestamp)
                })
            }

            prefs.edit()
                .putString(KEY_GEOFENCES, fenceArray.toString())
                .putString(KEY_HISTORY, historyArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving data", e)
        }
    }

    fun addGeofence(context: Context, geofence: Geofence) {
        _geofences.add(geofence)
        save(context)
    }

    fun removeGeofence(context: Context, geofence: Geofence) {
        _geofences.remove(geofence)
        save(context)
    }
    
    fun updateGeofenceState(id: String, newState: GeofenceState) {
        // State changes happen frequently in background, we might not want to write to disk 
        // every single time to save battery, but for correctness on kill/restart, we should.
        // For MVP, we'll update memory immediately, but maybe skip saving here if performance issues arise.
        val index = _geofences.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = _geofences[index]
            if (current.lastState != newState) {
                _geofences[index] = current.copy(lastState = newState)
                // Note: We need context to save. Since this method is often called from Service 
                // where we might not want to pass Context deeply every time, 
                // we'll leave the disk save for add/remove or explicit save calls for now,
                // OR we can rely on the service to call save.
            }
        }
    }
    
    // overload for service usage with context
    fun updateGeofenceState(context: Context, id: String, newState: GeofenceState) {
        val index = _geofences.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = _geofences[index]
            if (current.lastState != newState) {
                _geofences[index] = current.copy(lastState = newState)
                save(context)
            }
        }
    }
}
