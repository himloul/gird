package org.foss.gird

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GeofenceRepository {
    private const val TAG = "GeofenceRepository"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    
    private val _geofences = mutableStateListOf<Geofence>()
    val geofences: List<Geofence> get() = _geofences

    private const val PREFS_NAME = "geoapp_prefs"
    private const val KEY_GEOFENCES = "geofences_json"
    private const val KEY_HISTORY = "history_json"
    private val _history = mutableStateListOf<GeofenceEvent>()
    val history: List<GeofenceEvent> get() = _history

    fun addEvent(context: Context, event: GeofenceEvent) {
        synchronized(lock) {
            _history.add(0, event) // Add to top
            if (_history.size > 100) _history.removeLast() // Keep last 100
        }
        save(context)
    }

    fun clearHistory(context: Context) {
        synchronized(lock) {
            _history.clear()
        }
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
        repositoryScope.launch {
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
    }

    fun loadMapState(context: Context): Triple<Double, Double, Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_LAT, java.lang.Double.doubleToRawLongBits(40.7128)))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_LON, java.lang.Double.doubleToRawLongBits(-74.0060)))
        val zoom = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(12.0)))
        return Triple(lat, lon, zoom)
    }

    fun load(context: Context) {
        repositoryScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // Load Fences
                val fenceJson = prefs.getString(KEY_GEOFENCES, null)
                val loadedFences = mutableListOf<Geofence>()
                if (fenceJson != null) {
                    val jsonArray = JSONArray(fenceJson)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        loadedFences.add(Geofence(
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
                val loadedHistory = mutableListOf<GeofenceEvent>()
                if (historyJson != null) {
                    val jsonArray = JSONArray(historyJson)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        loadedHistory.add(GeofenceEvent(
                            id = obj.getString("id"),
                            fenceName = obj.getString("name"),
                            eventType = GeofenceState.valueOf(obj.getString("type")),
                            timestamp = obj.getLong("time")
                        ))
                    }
                }

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    synchronized(lock) {
                        _geofences.clear()
                        _geofences.addAll(loadedFences)
                        _history.clear()
                        _history.addAll(loadedHistory)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
            }
        }
    }

    fun save(context: Context) {
        // Deep copy the lists to avoid concurrent modification issues during background save
        val fenceSnapshot = synchronized(lock) { _geofences.toList() }
        val historySnapshot = synchronized(lock) { _history.toList() }

        repositoryScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // Save Fences
                val fenceArray = JSONArray()
                fenceSnapshot.forEach { fence ->
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
                historySnapshot.forEach { event ->
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
    }

    fun addGeofence(context: Context, geofence: Geofence) {
        synchronized(lock) {
            _geofences.add(geofence)
        }
        save(context)
    }

    fun removeGeofence(context: Context, geofence: Geofence) {
        synchronized(lock) {
            _geofences.remove(geofence)
        }
        save(context)
    }

    fun toggleGeofence(context: Context, id: String, isActive: Boolean) {
        synchronized(lock) {
            val index = _geofences.indexOfFirst { it.id == id }
            if (index != -1) {
                _geofences[index] = _geofences[index].copy(isActive = isActive)
            }
        }
        save(context)
    }
    
    fun updateGeofenceState(id: String, newState: GeofenceState) {
        synchronized(lock) {
            val index = _geofences.indexOfFirst { it.id == id }
            if (index != -1) {
                val current = _geofences[index]
                if (current.lastState != newState) {
                    _geofences[index] = current.copy(lastState = newState)
                }
            }
        }
    }
    
    // overload for service usage with context
    fun updateGeofenceState(context: Context, id: String, newState: GeofenceState) {
        synchronized(lock) {
            val index = _geofences.indexOfFirst { it.id == id }
            if (index != -1) {
                val current = _geofences[index]
                if (current.lastState != newState) {
                    _geofences[index] = current.copy(lastState = newState)
                }
            }
        }
    }
}
