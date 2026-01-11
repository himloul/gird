package org.foss.gird

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.compose.material3.MaterialTheme
import android.graphics.Color
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.ScaleBarOverlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable

@Composable
fun MapViewContainer(
    modifier: Modifier = Modifier,
    onMapClick: (GeoPoint) -> Unit = {},
    onGeofenceClick: (Geofence) -> Unit = {},
    geofences: List<Geofence> = emptyList(),
    selectedPoint: GeoPoint? = null,
    panToMyLocationTrigger: Int = 0,
    isMonitoringActive: Boolean = false,
    initialCenter: GeoPoint = GeoPoint(40.7128, -74.0060),
    initialZoom: Double = 12.0
) {
    val context = LocalContext.current
    
    // Natural Palette (Muted if service is off)
    val colorFilter = if (isMonitoringActive) 1.0f else 0.4f
    val forestGreen = androidx.compose.ui.graphics.Color(0xFF2E7D32).copy(alpha = colorFilter).toArgb()
    val blueColor = androidx.compose.ui.graphics.Color(0xFF1976D2).copy(alpha = colorFilter).toArgb()
    val redColor = androidx.compose.ui.graphics.Color(0xFFD32F2F).copy(alpha = colorFilter).toArgb()
    
    val draftColor = MaterialTheme.colorScheme.tertiary.toArgb()
    
    // Programmatic Circular Marker Drawer
    fun createCircleMarker(color: Int, sizeDp: Int = 16): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (sizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = size / 2.5f

        // White Stroke
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            this.style = Paint.Style.STROKE
            this.strokeWidth = 2f * density
        }
        
        // Solid Core
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.FILL
        }

        canvas.drawCircle(center, center, radius, fillPaint)
        canvas.drawCircle(center, center, radius, strokePaint)
        
        return BitmapDrawable(context.resources, bitmap)
    }

    // Initialize Osmdroid Configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            
            controller.setZoom(initialZoom)
            controller.setCenter(initialCenter)
            
            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
            locationOverlay.enableMyLocation()
            overlays.add(locationOverlay)

            // Add Compass
            val compassOverlay = CompassOverlay(context, InternalCompassOrientationProvider(context), this)
            compassOverlay.enableCompass()
            
            // Move to Bottom Left (approx 45dp from left, and near the bottom)
            val dm = context.resources.displayMetrics
            // X is pixels from left, Y is pixels from TOP
            // To be at bottom, Y must be close to heightPixels
            compassOverlay.setCompassCenter(45f * dm.density, dm.heightPixels - (150f * dm.density)) 
            
            overlays.add(compassOverlay)

            addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                    saveState()
                    return true
                }
                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                    saveState()
                    return true
                }
                private fun saveState() {
                    val center = mapCenter as GeoPoint
                    GeofenceRepository.saveMapState(context, center.latitude, center.longitude, zoomLevelDouble)
                }
            })
        }
    }

    // Handle "My Location" snap request
    LaunchedEffect(panToMyLocationTrigger) {
        if (panToMyLocationTrigger > 0) {
            val locationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
            val myLocation = locationOverlay?.myLocation
            if (myLocation != null) {
                mapView.controller.setCenter(myLocation)
                mapView.controller.setZoom(16.0)
            } else {
                Toast.makeText(context, "Searching for GPS signal...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            // Keep specialized overlays, clear dynamic markers/circles
            val specialOverlays = view.overlays.filter { 
                it is MyLocationNewOverlay || it is CompassOverlay || it is ScaleBarOverlay 
            }
            view.overlays.clear()
            view.overlays.addAll(specialOverlays)

            // 1. Add map listener for clicking FIRST (Bottom of stack)
            val eventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    p?.let { onMapClick(it) }
                    return true
                }
                override fun longPressHelper(p: GeoPoint?): Boolean = false
            })
            view.overlays.add(eventsOverlay)
            
            // 2. Draw Saved Geofences
            geofences.forEach { fence ->
                val center = GeoPoint(fence.latitude, fence.longitude)
                
                val mColor = when(fence.color) {
                    GeofenceColor.RED -> redColor
                    GeofenceColor.BLUE -> blueColor
                    GeofenceColor.GREEN -> forestGreen
                }

                // Add marker
                val marker = Marker(view).apply {
                    position = center
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createCircleMarker(mColor)
                    infoWindow = null 
                    setOnMarkerClickListener { _, _ ->
                        onGeofenceClick(fence)
                        true 
                    }
                }
                view.overlays.add(marker)

                // Add circle
                val circle = Polygon.pointsAsCircle(center, fence.radiusInMeters.toDouble())
                val polygon = Polygon(view).apply {
                    points = circle
                    fillColor = Color.argb(if (isMonitoringActive) 45 else 20, Color.red(mColor), Color.green(mColor), Color.blue(mColor))
                    strokeColor = mColor
                    strokeWidth = 3f
                    setOnClickListener { _, _, _ ->
                        onGeofenceClick(fence)
                        true 
                    }
                }
                view.overlays.add(polygon)
            }

            // 3. Draw Selected Point (Draft)
            if (selectedPoint != null) {
                val draftMarker = Marker(view).apply {
                    position = selectedPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createCircleMarker(draftColor, 20) // Slightly larger for draft
                    infoWindow = null
                }
                view.overlays.add(draftMarker)
            }
            
            view.invalidate()
        }
    )
}
