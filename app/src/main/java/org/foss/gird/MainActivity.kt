package org.foss.gird

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.util.GeoPoint
import java.util.*

import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GirdTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("map") }
    
    var selectedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedGeofence by remember { mutableStateOf<Geofence?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var panToMyLocationTrigger by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        GeofenceRepository.load(context)
    }

    val locationPermissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    val backgroundLocationState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        null
    }

    val geofences = GeofenceRepository.geofences
    val initialMapState = remember { GeofenceRepository.loadMapState(context) }
    
    var isMonitoringActive by remember { 
        mutableStateOf(context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE).getBoolean("is_monitoring_active", false)) 
    }

    LaunchedEffect(isMonitoringActive) {
        context.getSharedPreferences("geoapp_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_monitoring_active", isMonitoringActive)
            .apply()

        val intent = Intent(context, LocationService::class.java)
        if (isMonitoringActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                val destinations = listOf(
                    Triple("map", "Map", Icons.Default.Map),
                    Triple("fences", "Fences", Icons.Default.Layers),
                    Triple("history", "History", Icons.Default.History),
                    Triple("settings", "Settings", Icons.Default.Settings)
                )

                destinations.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentScreen == route,
                        onClick = { currentScreen = route },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (route == "fences" && geofences.isNotEmpty()) {
                                        Badge { Text(geofences.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentScreen == "map") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    FloatingActionButton(
                        onClick = { isMonitoringActive = !isMonitoringActive },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        contentColor = if (isMonitoringActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Icon(
                            if (isMonitoringActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            null
                        )
                    }

                    FloatingActionButton(
                        onClick = { panToMyLocationTrigger++ },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentScreen) {
                "fences" -> FencesScreen(onBack = { currentScreen = "map" })
                "history" -> HistoryScreen(onBack = { currentScreen = "map" })
                "settings" -> SettingsScreen(onBack = { currentScreen = "map" })
                else -> {
                    if (locationPermissionsState.allPermissionsGranted) {
                        MapViewContainer(
                            modifier = Modifier.fillMaxSize(),
                            geofences = geofences,
                            selectedPoint = selectedPoint,
                            panToMyLocationTrigger = panToMyLocationTrigger,
                            isMonitoringActive = isMonitoringActive,
                            initialCenter = GeoPoint(initialMapState.first, initialMapState.second),
                            initialZoom = initialMapState.third,
                            onMapClick = { point ->
                                selectedGeofence = null
                                selectedPoint = point
                                showBottomSheet = true
                            },
                            onGeofenceClick = { fence ->
                                selectedPoint = null
                                selectedGeofence = fence
                                showBottomSheet = true
                            }
                        )

                        // Background Warning (Near bottom above FAB stack)
                        if (backgroundLocationState != null && !backgroundLocationState.status.isGranted) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                                    .width(200.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Background required", style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = { backgroundLocationState.launchPermissionRequest() }) {
                                        Text("Fix Now")
                                    }
                                }
                            }
                        }

                        if (showBottomSheet && (selectedPoint != null || selectedGeofence != null)) {
                            ModalBottomSheet(
                                onDismissRequest = {
                                    showBottomSheet = false
                                    selectedPoint = null
                                    selectedGeofence = null
                                }
                            ) {
                                val pt = selectedGeofence?.let { GeoPoint(it.latitude, it.longitude) } ?: selectedPoint!!
                                LocationDetailSheet(
                                    geoPoint = pt,
                                    existingGeofence = selectedGeofence,
                                    onSave = { f ->
                                        if (selectedGeofence != null) GeofenceRepository.removeGeofence(context, selectedGeofence!!)
                                        GeofenceRepository.addGeofence(context, f)
                                        showBottomSheet = false; selectedPoint = null; selectedGeofence = null
                                    },
                                    onDelete = { f ->
                                        GeofenceRepository.removeGeofence(context, f)
                                        showBottomSheet = false; selectedPoint = null; selectedGeofence = null
                                    },
                                    onDismiss = { showBottomSheet = false; selectedPoint = null; selectedGeofence = null }
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PermissionRequestScreen { locationPermissionsState.launchMultiplePermissionRequest() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Location Access", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Gird needs location access to monitor fences.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Permissions")
        }
    }
}
