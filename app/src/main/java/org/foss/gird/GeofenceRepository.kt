package org.foss.gird

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GeofenceRepository {
    private const val TAG = "GeofenceRepository"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _geofences = mutableStateListOf<Geofence>()
    val geofences: List<Geofence> get() = _geofences

    private val _history = mutableStateListOf<GeofenceEvent>()
    val history: List<GeofenceEvent> get() = _history

    private val _tasks = mutableStateListOf<Task>()
    val tasks: List<Task> get() = _tasks

    private lateinit var database: AppDatabase

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)
        
        // Start observing database changes
        repositoryScope.launch {
            database.geofenceDao().getAll().collectLatest { list ->
                withContext(Dispatchers.Main) {
                    _geofences.clear()
                    _geofences.addAll(list)
                }
            }
        }
        
        repositoryScope.launch {
            database.taskDao().getAll().collectLatest { list ->
                withContext(Dispatchers.Main) {
                    _tasks.clear()
                    _tasks.addAll(list)
                }
            }
        }
        
        repositoryScope.launch {
            database.eventDao().getAll().collectLatest { list ->
                withContext(Dispatchers.Main) {
                    _history.clear()
                    _history.addAll(list)
                }
            }
        }

        // Run migration if needed
        repositoryScope.launch {
            migrateLegacyData(context)
        }
    }

    private suspend fun migrateLegacyData(context: Context) {
        val prefs = context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("geofences_json")) return

        Log.d(TAG, "Starting legacy data migration...")
        
        try {
            // Fences
            prefs.getString("geofences_json", null)?.let { json ->
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    database.geofenceDao().insert(Geofence(
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

            // Tasks
            prefs.getString("tasks_json", null)?.let { json ->
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    database.taskDao().insert(Task(
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

            // History
            prefs.getString("history_json", null)?.let { json ->
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    database.eventDao().insert(GeofenceEvent(
                        id = obj.getString("id"),
                        fenceName = obj.getString("name"),
                        eventType = GeofenceState.valueOf(obj.getString("type")),
                        timestamp = obj.getLong("time")
                    ))
                }
            }

            // Clear legacy data to avoid re-migration
            prefs.edit()
                .remove("geofences_json")
                .remove("history_json")
                .remove("tasks_json")
                .apply()
                
            Log.d(TAG, "Migration completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
        }
    }

    fun addTask(context: Context, task: Task) {
        repositoryScope.launch {
            database.taskDao().insert(task)
        }
    }

    fun addTaskByQuery(context: Context, query: String, activeFenceId: String? = null) {
        if (query.isBlank()) return
        
        repositoryScope.launch {
            var content = query
            var fenceId: String? = null
            var isRecurring = false
            
            if (content.contains("!daily") || content.contains("!frequent")) {
                isRecurring = true
                content = content.replace("!daily", "").replace("!frequent", "").trim()
            }

            val atIndex = content.lastIndexOf("@")
            if (atIndex != -1) {
                val locationName = content.substring(atIndex + 1).trim()
                val matchedFence = _geofences.find { it.name.equals(locationName, ignoreCase = true) }
                if (matchedFence != null) {
                    fenceId = matchedFence.id
                    content = content.substring(0, atIndex).trim()
                }
            }

            if (fenceId == null) {
                fenceId = activeFenceId
            }

            database.taskDao().insert(Task(
                content = content,
                fenceId = fenceId,
                isRecurring = isRecurring
            ))
        }
    }

    fun removeTask(context: Context, task: Task) {
        repositoryScope.launch {
            database.taskDao().delete(task)
        }
    }

    fun toggleTaskCompletion(context: Context, id: String, isCompleted: Boolean) {
        repositoryScope.launch {
            database.taskDao().updateCompletion(
                id, 
                isCompleted, 
                if (isCompleted) System.currentTimeMillis() else null
            )
        }
    }

    fun updateTask(context: Context, updatedTask: Task) {
        repositoryScope.launch {
            database.taskDao().insert(updatedTask)
        }
    }

    fun addEvent(context: Context, event: GeofenceEvent) {
        repositoryScope.launch {
            database.eventDao().insert(event)
        }
    }

    fun clearHistory(context: Context) {
        repositoryScope.launch {
            database.eventDao().deleteAll()
            // Reset task completion narrative
            val currentTasks = _tasks.toList()
            currentTasks.forEach {
                if (it.completedAt != null) {
                    database.taskDao().insert(it.copy(completedAt = null))
                }
            }
        }
    }

    // Polling mode stays in Prefs as it's a simple setting
    fun savePollingMode(context: Context, mode: String) {
        context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE).edit()
            .putString("polling_mode", mode)
            .apply()
    }

    fun loadPollingMode(context: Context): String {
        return context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE)
            .getString("polling_mode", "Balanced") ?: "Balanced"
    }

    fun saveMapState(context: Context, lat: Double, lon: Double, zoom: Double) {
        repositoryScope.launch {
            context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE).edit()
                .putLong("map_lat", java.lang.Double.doubleToRawLongBits(lat))
                .putLong("map_lon", java.lang.Double.doubleToRawLongBits(lon))
                .putLong("map_zoom", java.lang.Double.doubleToRawLongBits(zoom))
                .apply()
        }
    }

    fun loadMapState(context: Context): Triple<Double, Double, Double> {
        val prefs = context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE)
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong("map_lat", java.lang.Double.doubleToRawLongBits(40.7128)))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong("map_lon", java.lang.Double.doubleToRawLongBits(-74.0060)))
        val zoom = java.lang.Double.longBitsToDouble(prefs.getLong("map_zoom", java.lang.Double.doubleToRawLongBits(12.0)))
        return Triple(lat, lon, zoom)
    }

    // Legacy support for backward compatibility with existing calls
    fun load(context: Context) {
        if (!::database.isInitialized) {
            init(context)
        }
    }

    fun addGeofence(context: Context, geofence: Geofence) {
        repositoryScope.launch {
            database.geofenceDao().insert(geofence)
        }
    }

    fun removeGeofence(context: Context, geofence: Geofence) {
        repositoryScope.launch {
            database.geofenceDao().delete(geofence)
        }
    }

    fun toggleGeofence(context: Context, id: String, isActive: Boolean) {
        repositoryScope.launch {
            database.geofenceDao().toggleActive(id, isActive)
        }
    }
    
    fun updateGeofenceState(context: Context, id: String, newState: GeofenceState) {
        repositoryScope.launch {
            database.geofenceDao().updateState(id, newState)
        }
    }

    fun exportDataToJson(): String {
        // Since this is a utility function, we can just use current snapshot
        val root = JSONObject()
        val fenceArray = JSONArray()
        _geofences.forEach { fence ->
            fenceArray.put(JSONObject().apply {
                put("id", fence.id)
                put("name", fence.name)
                put("latitude", fence.latitude)
                put("longitude", fence.longitude)
                put("radius", fence.radiusInMeters)
            })
        }
        val historyArray = JSONArray()
        _history.forEach { event ->
            historyArray.put(JSONObject().apply {
                put("fence", event.fenceName)
                put("type", event.eventType.name)
                put("timestamp", event.timestamp)
            })
        }
        val taskArray = JSONArray()
        _tasks.forEach { task ->
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
}
