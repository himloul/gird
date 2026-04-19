package org.foss.gird

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.UUID
import java.util.Calendar
import kotlin.math.*

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentInterval: Long = -1L
    private var currentProvider: String? = null
    private var currentMinDistance: Float = -1f
    
    // Physics constants
    private val MIN_POLL_INTERVAL = 10000L
    private val MAX_POLL_INTERVAL = 900000L
    private val SAFETY_FACTOR = 0.5

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_monitoring_active", false)) {
            stopSelf()
            return
        }
        GeofenceRepository.load(this)
        GeofenceRepository.geofences.forEach { serviceStateCache[it.id] = it.lastState }
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startForeground(NOTIFICATION_ID, createNotification("Dynamic monitoring active"))
        startTracking()
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        try {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 30000L, 0f, this)
            adjustPollingRate(null) 
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun adjustPollingRate(currentLocation: Location?) {
        val mode = GeofenceRepository.loadPollingMode(this)
        val activeFences = GeofenceRepository.geofences.filter { it.isActive }
        if (activeFences.isEmpty()) {
            updateRequest(MAX_POLL_INTERVAL, 500f, mode) 
            return
        }
        if (currentLocation == null) {
            updateRequest(60000L, 10f, mode) 
            return
        }
        var minDistanceToBoundary = Double.MAX_VALUE
        activeFences.forEach { fence ->
            val distToCenter = GeofenceUtils.calculateDistance(currentLocation.latitude, currentLocation.longitude, fence.latitude, fence.longitude)
            val distToEdge = max(0.0, distToCenter - fence.radiusInMeters)
            if (distToEdge < minDistanceToBoundary) minDistanceToBoundary = distToEdge
        }
        val velocity = if (currentLocation.hasSpeed()) currentLocation.speed.toDouble() else {
            val last = lastLocation
            if (last != null) {
                val d = currentLocation.distanceTo(last).toDouble()
                val t = (currentLocation.time - last.time) / 1000.0
                if (t > 0) d / t else 0.0
            } else 1.0
        }
        val calculatedIntervalMs = if (velocity > 0.1) (minDistanceToBoundary / velocity) * 1000.0 * SAFETY_FACTOR else MAX_POLL_INTERVAL.toDouble()
        val finalInterval = when (mode) {
            "High Precision" -> calculatedIntervalMs.toLong().coerceIn(MIN_POLL_INTERVAL, 120000L)
            "Battery Saver" -> calculatedIntervalMs.toLong().coerceIn(300000L, MAX_POLL_INTERVAL)
            else -> calculatedIntervalMs.toLong().coerceIn(30000L, MAX_POLL_INTERVAL)
        }
        val minDistance = (minDistanceToBoundary * 0.1).toFloat().coerceIn(10f, 500f)
        updateRequest(finalInterval, minDistance, mode)
    }

    @SuppressLint("MissingPermission")
    private fun updateRequest(interval: Long, minDistance: Float, mode: String) {
        try {
            val provider = if (interval < 60000L || mode == "High Precision") LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            if (provider == currentProvider && abs(interval - currentInterval) < 5000L) return
            locationManager.removeUpdates(this)
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 30000L, 0f, this)
            locationManager.requestLocationUpdates(provider, interval, minDistance, this)
            currentProvider = provider
            currentInterval = interval
            currentMinDistance = minDistance
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 200f) return
        val last = lastLocation
        if (last != null && location.distanceTo(last) < 2f) return
        lastLocation = location
        if (wakeLock == null) wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gird:LocProc")
        wakeLock?.acquire(3000L)
        checkGeofences(location)
        adjustPollingRate(location)
    }

    private val serviceStateCache = mutableMapOf<String, GeofenceState>()
    private val lastTriggerTimes = mutableMapOf<String, Long>()

    private fun checkGeofences(location: Location) {
        GeofenceRepository.geofences.filter { it.isActive }.forEach { fence ->
            val distance = GeofenceUtils.calculateDistance(location.latitude, location.longitude, fence.latitude, fence.longitude)
            val buffer = (location.accuracy * 0.5).coerceAtLeast(5.0)
            val isInside = distance <= (fence.radiusInMeters + buffer)
            val currentState = serviceStateCache[fence.id] ?: fence.lastState
            val newState = if (isInside) GeofenceState.INSIDE else GeofenceState.OUTSIDE
            if (newState != currentState) {
                val lastTrigger = lastTriggerTimes[fence.id] ?: 0L
                if (System.currentTimeMillis() - lastTrigger > 30000L) {
                    serviceStateCache[fence.id] = newState
                    lastTriggerTimes[fence.id] = System.currentTimeMillis()
                    GeofenceRepository.updateGeofenceState(this, fence.id, newState)
                    val event = GeofenceEvent(UUID.randomUUID().toString(), fence.name, newState)
                    GeofenceRepository.addEvent(this, event)
                    sendGeofenceNotification(fence, newState)
                }
            }
        }
    }

    private fun sendGeofenceNotification(fence: Geofence, state: GeofenceState) {
        val title = if (state == GeofenceState.INSIDE) "Arrived at ${fence.name}" else "Left ${fence.name}"
        val pendingTasks = GeofenceRepository.tasks.filter { it.fenceId == fence.id && !it.isCompleted }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val relevantTasks = pendingTasks.filter { task ->
            if (task.startTime != null && task.endTime != null) currentHour in task.startTime..task.endTime else true
        }

        if (state == GeofenceState.OUTSIDE && pendingTasks.isNotEmpty()) return

        val body = if (relevantTasks.isNotEmpty()) "You have ${relevantTasks.size} tasks here: ${relevantTasks.first().content}..." else "Context active."

        val channel = NotificationChannel(ALERT_CHANNEL_ID, "Geofence Alerts", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_custom_pin)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Step 1: Interactive Action
        if (state == GeofenceState.INSIDE && relevantTasks.isNotEmpty()) {
            val task = relevantTasks.first()
            val actionIntent = Intent(this, TaskActionReceiver::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("fence_id", fence.id)
            }
            val pendingAction = PendingIntent.getBroadcast(
                this, 
                task.id.hashCode(), 
                actionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_edit, "Mark as Done", pendingAction)
        }

        notificationManager.notify(fence.id.hashCode(), builder.build())
    }

    private fun createNotification(content: String): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Location Service", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gird")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "location_channel"
        const val ALERT_CHANNEL_ID = "alert_channel"
        const val NOTIFICATION_ID = 12345
    }
}
