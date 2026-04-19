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
    private const val KEY_TASKS = "tasks_json"
    
    private val _history = mutableStateListOf<GeofenceEvent>()
    val history: List<GeofenceEvent> get() = _history

    private val _tasks = mutableStateListOf<Task>()
    val tasks: List<Task> get() = _tasks

    fun addTask(context: Context, task: Task) {
        synchronized(lock) {
            _tasks.add(0, task)
        }
        save(context)
    }

    /**
     * Parses a query string with context awareness.
     * @param activeFenceId If provided, task will be auto-tagged here if no @ is found.
     */
    fun addTaskByQuery(context: Context, query: String, activeFenceId: String? = null) {
        if (query.isBlank()) return
        
        var content = query
        var fenceId: String? = null
        var isRecurring = false
        
        // 1. Parse Recurring Flag (!)
        if (content.contains("!daily") || content.contains("!frequent")) {
            isRecurring = true
            content = content.replace("!daily", "").replace("!frequent", "").trim()
        }

        // 2. Parse @Location
        val atIndex = content.lastIndexOf("@")
        if (atIndex != -1) {
            val locationName = content.substring(atIndex + 1).trim()
            val matchedFence = _geofences.find { it.name.equals(locationName, ignoreCase = true) }
            if (matchedFence != null) {
                fenceId = matchedFence.id
                content = content.substring(0, atIndex).trim()
            }
        }

        // 3. Context Auto-Tagging
        // If no explicit @ found, use the current active location
        if (fenceId == null) {
            fenceId = activeFenceId
        }

        addTask(context, Task(
            content = content,
            fenceId = fenceId,
            isRecurring = isRecurring
        ))
    }

    fun removeTask(context: Context, task: Task) {
        synchronized(lock) {
            _tasks.remove(task)
        }
        save(context)
    }

    fun toggleTaskCompletion(context: Context, id: String, isCompleted: Boolean) {
        synchronized(lock) {
            val index = _tasks.indexOfFirst { it.id == id }
            if (index != -1) {
                _tasks[index] = _tasks[index].copy(
                    isCompleted = isCompleted,
                    completedAt = if (isCompleted) System.currentTimeMillis() else null
                )
            }
        }
        save(context)
    }

    fun updateTask(context: Context, updatedTask: Task) {
        synchronized(lock) {
            val index = _tasks.indexOfFirst { it.id == updatedTask.id }
            if (index != -1) {
                _tasks[index] = updatedTask
            }
        }
        save(context)
    }

    fun addEvent(context: Context, event: GeofenceEvent) {
        synchronized(lock) {
            _history.add(0, event) // Add to top
            if (_history.size > 200) _history.removeLast()
        }
        save(context)
    }

    fun clearHistory(context: Context) {
        synchronized(lock) {
            _history.clear()
            for (i in _tasks.indices) {
                if (_tasks[i].completedAt != null) {
                    _tasks[i] = _tasks[i].copy(completedAt = null)
                }
            }
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

                // Load Tasks
                val tasksJson = prefs.getString(KEY_TASKS, null)
                val loadedTasks = mutableListOf<Task>()
                if (tasksJson != null) {
                    val jsonArray = JSONArray(tasksJson)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        loadedTasks.add(Task(
                            id = obj.getString("id"),
                            fenceId = if (obj.has("fenceId")) obj.optString("fenceId", "") else null,
                            content = obj.getString("content"),
                            isCompleted = obj.getBoolean("isCompleted"),
                            timestamp = obj.getLong("time"),
                            dueDate = if (obj.has("dueDate")) obj.getLong("dueDate") else null,
                            startTime = if (obj.has("startTime")) obj.getInt("startTime") else null,
                            endTime = if (obj.has("endTime")) obj.getInt("endTime") else null,
                            completedAt = if (obj.has("completedAt")) obj.getLong("completedAt") else null,
                            isRecurring = if (obj.has("isRecurring")) obj.getBoolean("isRecurring") else false
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
                        _tasks.clear()
                        _tasks.addAll(loadedTasks)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
            }
        }
    }

    fun save(context: Context) {
        val fenceSnapshot = synchronized(lock) { _geofences.toList() }
        val historySnapshot = synchronized(lock) { _history.toList() }
        val taskSnapshot = synchronized(lock) { _tasks.toList() }

        repositoryScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
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

                val historyArray = JSONArray()
                historySnapshot.forEach { event ->
                    historyArray.put(JSONObject().apply {
                        put("id", event.id)
                        put("name", event.fenceName)
                        put("type", event.eventType.name)
                        put("time", event.timestamp)
                    })
                }

                val taskArray = JSONArray()
                taskSnapshot.forEach { task ->
                    taskArray.put(JSONObject().apply {
                        put("id", task.id)
                        put("fenceId", task.fenceId ?: "")
                        put("content", task.content)
                        put("isCompleted", task.isCompleted)
                        put("time", task.timestamp)
                        task.dueDate?.let { put("dueDate", it) }
                        task.startTime?.let { put("startTime", it) }
                        task.endTime?.let { put("endTime", it) }
                        task.completedAt?.let { put("completedAt", it) }
                        put("isRecurring", task.isRecurring)
                    })
                }

                prefs.edit()
                    .putString(KEY_GEOFENCES, fenceArray.toString())
                    .putString(KEY_HISTORY, historyArray.toString())
                    .putString(KEY_TASKS, taskArray.toString())
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving data", e)
            }
        }
    }

    fun exportDataToJson(): String {
        val root = JSONObject()
        val fenceSnapshot = synchronized(lock) { _geofences.toList() }
        val historySnapshot = synchronized(lock) { _history.toList() }
        val taskSnapshot = synchronized(lock) { _tasks.toList() }

        val fenceArray = JSONArray()
        fenceSnapshot.forEach { fence ->
            fenceArray.put(JSONObject().apply {
                put("id", fence.id)
                put("name", fence.name)
                put("latitude", fence.latitude)
                put("longitude", fence.longitude)
                put("radius", fence.radiusInMeters)
            })
        }

        val historyArray = JSONArray()
        historySnapshot.forEach { event ->
            historyArray.put(JSONObject().apply {
                put("fence", event.fenceName)
                put("type", event.eventType.name)
                put("timestamp", event.timestamp)
            })
        }

        val taskArray = JSONArray()
        taskSnapshot.forEach { task ->
            taskArray.put(JSONObject().apply {
                put("content", task.content)
                put("isCompleted", task.isCompleted)
                put("completedAt", task.completedAt)
            })
        }

        root.put("geofences", fenceArray)
        root.put("history", historyArray)
        root.put("tasks", taskArray)
        root.put("exported_at", System.currentTimeMillis())

        return root.toString(4) 
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
