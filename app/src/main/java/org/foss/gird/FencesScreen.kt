package org.foss.gird

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import java.util.*

import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val geofences = GeofenceRepository.geofences
    
    // Handle hardware back button
    BackHandler(onBack = onBack)

    // Bottom Sheet State
    var showAddSheet by remember { mutableStateOf(false) }
    var currentPoint by remember { mutableStateOf<GeoPoint?>(null) }

    fun openAddSheetAtCurrentLocation() {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            @SuppressLint("MissingPermission")
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            currentPoint = lastLocation?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(0.0, 0.0)
            showAddSheet = true
        } catch (e: Exception) {
            Toast.makeText(context, "Location unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Geofences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openAddSheetAtCurrentLocation() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Fence")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Pick on Map CTA
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Map, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick Location on Map")
            }

            Text(
                "Active Fences (${geofences.size})", 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (geofences.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No geofences saved yet.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp) // Avoid FAB overlap
                ) {
                    items(geofences) { fence ->
                        GeofenceItem(fence, onDelete = { GeofenceRepository.removeGeofence(context, fence) })
                    }
                }
            }
        }

        // Add Sheet
        if (showAddSheet && currentPoint != null) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false }
            ) {
                LocationDetailSheet(
                    geoPoint = currentPoint!!,
                    onSave = { fence ->
                        GeofenceRepository.addGeofence(context, fence)
                        showAddSheet = false
                    },
                    onDismiss = { showAddSheet = false }
                )
            }
        }
    }
}

@Composable

fun GeofenceItem(fence: Geofence, onDelete: () -> Unit) {

    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),

    ) {



        ListItem(

            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),

            headlineContent = { 

                Text(fence.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) 

            },

            supportingContent = {

                Column {

                    Text("${String.format("%.4f", fence.latitude)}, ${String.format("%.4f", fence.longitude)}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Box(modifier = Modifier.size(10.dp).background(

                            when(fence.color) {

                                GeofenceColor.RED -> androidx.compose.ui.graphics.Color(0xFFD32F2F)

                                GeofenceColor.BLUE -> androidx.compose.ui.graphics.Color(0xFF1976D2)

                                GeofenceColor.GREEN -> androidx.compose.ui.graphics.Color(0xFF2E7D32)

                            },

                            shape = androidx.compose.foundation.shape.CircleShape

                        ))

                        Spacer(modifier = Modifier.width(8.dp))

                        Text("Radius: ${fence.radiusInMeters.toInt()}m", style = MaterialTheme.typography.labelSmall)

                    }

                }

            },

            trailingContent = {

                IconButton(onClick = onDelete) {

                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)

                }

            }

        )

    }

}
