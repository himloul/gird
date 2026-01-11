package org.foss.gird

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import java.util.UUID
import kotlin.math.roundToInt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailSheet(
    geoPoint: GeoPoint,
    existingGeofence: Geofence? = null,
    onSave: (Geofence) -> Unit,
    onDelete: (Geofence) -> Unit = {},
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existingGeofence?.name ?: "") }
    var radius by remember { mutableFloatStateOf(existingGeofence?.radiusInMeters ?: 100f) }
    var selectedColor by remember { mutableStateOf(existingGeofence?.color ?: GeofenceColor.GREEN) }
    var isActive by remember { mutableStateOf(existingGeofence?.isActive ?: true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding() // Respect bottom nav bar/gestures
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (existingGeofence != null) "Edit Geofence" else "Add Geofence",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Switch(
                checked = isActive,
                onCheckedChange = { isActive = it }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Name Input
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Geofence Name") },
            placeholder = { Text("e.g. Home, Office, Gym") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Coordinates Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Latitude", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(String.format("%.5f", geoPoint.latitude), style = MaterialTheme.typography.bodyLarge)
            }
            Column {
                Text("Longitude", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(String.format("%.5f", geoPoint.longitude), style = MaterialTheme.typography.bodyLarge)
            }
        }
        
        if (existingGeofence != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Current Status: ${existingGeofence.lastState}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (existingGeofence.lastState == GeofenceState.INSIDE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(24.dp))

        // Color Selection

        Text("Theme Color", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GeofenceColor.values().forEach { colorOption ->
                val colorHex = when(colorOption) {
                    GeofenceColor.RED -> Color(0xFFD32F2F)
                    GeofenceColor.BLUE -> Color(0xFF1976D2)
                    GeofenceColor.GREEN -> Color(0xFF2E7D32)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colorHex)
                        .clickable { selectedColor = colorOption }
                        .padding(4.dp)
                ) {
                    if (selectedColor == colorOption) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.4f)
                        ) {}
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Radius Slider
        Text(
            text = "Geofence Radius: ${radius.roundToInt()}m",
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = radius,
            onValueChange = { radius = it },
            valueRange = 50f..1000f,
            steps = 19
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("50m", style = MaterialTheme.typography.bodySmall)
            Text("1km", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Actions
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val finalName = if (name.isBlank()) "Fence ${UUID.randomUUID().toString().take(4)}" else name
                        val newFence = existingGeofence?.copy(
                            name = finalName, 
                            radiusInMeters = radius, 
                            color = selectedColor,
                            isActive = isActive
                        ) ?: Geofence(
                            id = UUID.randomUUID().toString(),
                            name = finalName,
                            latitude = geoPoint.latitude,
                            longitude = geoPoint.longitude,
                            radiusInMeters = radius,
                            color = selectedColor,
                            isActive = isActive
                        )
                        onSave(newFence)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (existingGeofence != null) "Update" else "Save Geofence")
                }
            }
            
            if (existingGeofence != null) {
                TextButton(
                    onClick = { onDelete(existingGeofence) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Geofence")
                }
            }
        }
    }
}
