package org.foss.gird

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.util.GeoPoint
import java.util.*
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("tasks") }
    
    var selectedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedGeofence by remember { mutableStateOf<Geofence?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var panToMyLocationTrigger by remember { mutableIntStateOf(0) }
    var targetLocation by remember { mutableStateOf<GeoPoint?>(null) }

    // Search State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    
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
        topBar = {
            if (isSearchActive && currentScreen == "map") {
                // Search Bar View
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                scope.launch {
                                    searchResults = MapSearchProvider.search(it)
                                }
                            },
                            placeholder = { Text("Search location...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                IconButton(onClick = { 
                                    isSearchActive = false
                                    searchQuery = ""
                                    searchResults = emptyList()
                                }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        )
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            when(currentScreen) {
                                "tasks" -> "Gird"
                                "map" -> "Boundaries"
                                "settings" -> "Settings"
                                else -> "Gird"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (currentScreen == "map") {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                        }
                        if (currentScreen != "settings") {
                            IconButton(onClick = { currentScreen = "settings" }) {
                                Icon(Icons.Outlined.Settings, "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen != "settings") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp // Flat Look
                ) {
                    val destinations = listOf(
                        Triple("tasks", "Tasks", Icons.Default.PlaylistAddCheck),
                        Triple("map", "Map", Icons.Default.Map)
                    )

                    destinations.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentScreen == route,
                            onClick = { 
                                currentScreen = route 
                                isSearchActive = false // Close search on tab switch
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (route == "tasks") {
                                            val pendingCount = GeofenceRepository.tasks.count { !it.isCompleted }
                                            if (pendingCount > 0) Badge { Text(pendingCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(icon, contentDescription = label)
                                }
                            },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
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
                        onClick = { panToMyLocationTrigger++ },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                    }

                    FloatingActionButton(
                        onClick = { isMonitoringActive = !isMonitoringActive },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isMonitoringActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Icon(
                            if (isMonitoringActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            null
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentScreen) {
                "tasks" -> TasksScreen(onNavigateToMap = { currentScreen = "map" })
                "settings" -> SettingsScreen(onBack = { currentScreen = "tasks" })
                else -> {
                    if (locationPermissionsState.allPermissionsGranted) {
                        Box {
                            MapViewContainer(
                                modifier = Modifier.fillMaxSize(),
                                geofences = geofences,
                                selectedGeofence = selectedGeofence,
                                selectedPoint = selectedPoint,
                                targetLocation = targetLocation,
                                panToMyLocationTrigger = panToMyLocationTrigger,
                                isMonitoringActive = isMonitoringActive,
                                initialCenter = GeoPoint(initialMapState.first, initialMapState.second),
                                initialZoom = initialMapState.third,
                                onMapClick = { point ->
                                    selectedGeofence = null
                                    selectedPoint = point
                                    showBottomSheet = true
                                    isSearchActive = false // Close results on click
                                },
                                onGeofenceClick = { fence ->
                                    selectedPoint = null
                                    selectedGeofence = fence
                                    showBottomSheet = true
                                    isSearchActive = false
                                }
                            )

                            // Search Results Overlay
                            if (isSearchActive && searchResults.isNotEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .align(Alignment.TopCenter),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                        items(searchResults) { result ->
                                            ListItem(
                                                headlineContent = { Text(result.name, maxLines = 2) },
                                                leadingContent = { Icon(Icons.Default.Place, null) },
                                                modifier = Modifier.clickable {
                                                    targetLocation = GeoPoint(result.latitude, result.longitude)
                                                    isSearchActive = false
                                                    searchResults = emptyList()
                                                    searchQuery = ""
                                                }
                                            )
                                        }
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
                        PermissionRequestScreen { locationPermissionsState.launchMultiplePermissionRequest() }
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
        Text("Location Access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Gird needs location access to monitor fences and tasks.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Permissions")
        }
    }
}
