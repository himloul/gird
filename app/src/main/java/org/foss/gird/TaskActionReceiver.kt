package org.foss.gird

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager

class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val fenceId = intent.getStringExtra("fence_id") ?: return
        
        // 1. Update data
        GeofenceRepository.load(context)
        GeofenceRepository.toggleTaskCompletion(context, taskId, true)
        
        // 2. Dismiss the specific notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(fenceId.hashCode())
    }
}
