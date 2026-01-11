package org.foss.gird

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Load geofences to check if we have any active ones? 
            // Or just start service and let it decide. 
            // For simplicity, we start the service if the user had it active.
            // We can check a "service_enabled" pref.
            
            val prefs = context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE)
            val isMonitoringActive = prefs.getBoolean("is_monitoring_active", false)

            if (isMonitoringActive) {
                val serviceIntent = Intent(context, LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
