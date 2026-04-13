package org.foss.gird

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.UUID

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

    override fun onCreate() {
        super.onCreate()
        
        // Lifecycle safeguard: Stop if monitoring was disabled while service was starting
        val prefs = getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_monitoring_active", false)) {
            stopSelf()
            return
        }

        GeofenceRepository.load(this)
        
        // Initialize cache from persisted state to prevent duplicate notifications
        GeofenceRepository.geofences.forEach { fence ->
            serviceStateCache[fence.id] = fence.lastState
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startForeground(NOTIFICATION_ID, createNotification("Monitoring boundaries..."))
        startTracking()
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        try {
            // Register Passive Provider (Battery "Hitchhiking")
            locationManager.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                30000L,
                0f,
                this
            )
            adjustPollingRate(null) 
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun adjustPollingRate(currentLocation: Location?) {
        val mode = GeofenceRepository.loadPollingMode(this)
        val fences = GeofenceRepository.geofences.filter { it.isActive }
        
        if (fences.isEmpty()) {
            val interval = if (mode == "Battery Saver") 900000L else 600000L
            updateRequest(interval, 500f, mode) 
            return
        }

        if (currentLocation == null) {
            updateRequest(60000L, 10f, mode) 
            return
        }

        var minDistance = Double.MAX_VALUE
        fences.forEach { fence ->
            val dist = GeofenceUtils.calculateDistance(
                currentLocation.latitude, currentLocation.longitude,
                fence.latitude, fence.longitude
            )
            val distToBoundary = kotlin.math.max(0.0, dist - fence.radiusInMeters)
            if (distToBoundary < minDistance) minDistance = distToBoundary
        }

        // Map distance to interval based on Mode
        val newInterval = when (mode) {
            "Battery Saver" -> when {
                minDistance < 1000 -> 300000L  // 5 min
                else -> 900000L                // 15 min
            }
            "High Precision" -> when {
                minDistance < 500 -> 15000L    // 15 sec
                minDistance < 2000 -> 30000L   // 30 sec
                else -> 120000L                // 2 min
            }
            else -> { // Balanced (Default)
                when {
                    minDistance < 500 -> 30000L    // 30 sec
                    minDistance < 2000 -> 60000L   // 1 min
                    minDistance < 10000 -> 300000L // 5 min
                    else -> 600000L                // 10 min
                }
            }
        }

        updateRequest(newInterval, 20f, mode)
    }

    @SuppressLint("MissingPermission")
    private fun updateRequest(interval: Long, minDistance: Float, mode: String) {
        try {
            val provider = when (mode) {
                "Battery Saver" -> LocationManager.NETWORK_PROVIDER
                "High Precision" -> LocationManager.GPS_PROVIDER
                else -> if (interval > 120000L) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            }

            // Only update if parameters actually changed to avoid provider reset churn
            if (provider == currentProvider && interval == currentInterval && minDistance == currentMinDistance) {
                return
            }

            locationManager.removeUpdates(this)
            
            // Keep passive provider active for "free" updates
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 30000L, 0f, this)
            
            locationManager.requestLocationUpdates(provider, interval, minDistance, this)
            
            currentProvider = provider
            currentInterval = interval
            currentMinDistance = minDistance
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        // Accuracy Filter: Ignore very low accuracy updates that could cause jumpy geofence triggers
        // If accuracy is worse than 100m, or worse than the smallest geofence radius, ignore it.
        val minRadius = GeofenceRepository.geofences.filter { it.isActive }.minOfOrNull { it.radiusInMeters } ?: 50f
        if (location.hasAccuracy() && location.accuracy > kotlin.math.max(100f, minRadius)) {
            return
        }

        val last = lastLocation
        if (last != null && location.distanceTo(last) < 5f) return
        
        lastLocation = location

        // Acquire WakeLock to ensure processing completes during deep sleep
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gird:LocationProcessing")
        }
        wakeLock?.acquire(5000L) // Auto-release after 5 seconds

        checkGeofences(location)
        adjustPollingRate(location)
    }

    private val lastTriggerTimes = mutableMapOf<String, Long>()
    private val serviceStateCache = mutableMapOf<String, GeofenceState>()

    private fun checkGeofences(location: Location) {
        val fences = GeofenceRepository.geofences
        fences.forEach { fence ->
            if (!fence.isActive) return@forEach
            
            // Initialize cache if needed
            if (!serviceStateCache.containsKey(fence.id)) {
                serviceStateCache[fence.id] = fence.lastState
            }

            val distance = GeofenceUtils.calculateDistance(
                location.latitude, location.longitude,
                fence.latitude, fence.longitude
            )

            val buffer = kotlin.math.max(2.0, fence.radiusInMeters * 0.15)
            val currentStateInCache = serviceStateCache[fence.id] ?: GeofenceState.UNKNOWN
            
            val newState = when {
                distance < fence.radiusInMeters -> GeofenceState.INSIDE
                distance > (fence.radiusInMeters + buffer) -> GeofenceState.OUTSIDE
                else -> currentStateInCache // Stay in previous state while in buffer
            }

            if (newState != currentStateInCache && newState != GeofenceState.UNKNOWN) {
                val now = System.currentTimeMillis()
                val lastTrigger = lastTriggerTimes[fence.id] ?: 0L
                if (now - lastTrigger < 5000L) return@forEach // 5s throttle
                
                lastTriggerTimes[fence.id] = now
                serviceStateCache[fence.id] = newState // Update local cache IMMEDIATELY

                // Log Event
                GeofenceRepository.addEvent(this, GeofenceEvent(
                    id = UUID.randomUUID().toString(),
                    fenceName = fence.name,
                    eventType = newState
                ))
                
                sendGeofenceNotification(fence, newState)
                GeofenceRepository.updateGeofenceState(this, fence.id, newState)
            }
        }
    }
    
    private fun sendGeofenceNotification(fence: Geofence, state: GeofenceState) {
        val title = if (state == GeofenceState.INSIDE) "Entered ${fence.name}" else "Exited ${fence.name}"
        val message = "You have ${if (state == GeofenceState.INSIDE) "arrived at" else "left"} your monitored area."
        
        // Ensure high-importance channel exists for sound
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Boundary Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                enableVibration(true)
                // System default sound is applied by default for HIGH importance
            }
            notificationManager.createNotificationChannel(alertChannel)
        }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL) // Enables default sound, vibration, and lights
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(fence.id.hashCode(), notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }
    
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun createNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Boundary Monitor", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gird Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "location_channel"
        const val ALERT_CHANNEL_ID = "alert_channel"
        const val NOTIFICATION_ID = 12345
    }
}