package com.team404.dualshield.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.key
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.api.Zone
import com.team404.dualshield.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.google.android.gms.maps.model.PolylineOptions
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.isSystemInDarkTheme

@androidx.compose.ui.ExperimentalComposeUiApi
@SuppressLint("MissingPermission", "SetJavaScriptEnabled")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { BackendApi.create() }

    // ── States ─────────────────────────────────────────────────────────────
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLng by remember { mutableStateOf<Double?>(null) }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    var destinationText by remember { mutableStateOf("") }
    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>?>(null) }
    var speedKmh by remember { mutableStateOf(0) }
    var isHighRisk by remember { mutableStateOf(false) }
    var shareEnabled by remember { mutableStateOf(true) }
    var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
    var gpsReady by remember { mutableStateOf(false) }
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var apiKeyStatus by remember { mutableStateOf("Checking...") }
    var isGoogleMapsAvailable by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val geocoder = remember { android.location.Geocoder(context, java.util.Locale.getDefault()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val bhopal = LatLng(23.2599, 77.4126)
    val midPoint = LatLng(23.2500, 77.3500) // Near Neelbad/Central
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(midPoint, 11f)
    }

    val onSearchDestination = {
        if (destinationText.isNotBlank()) {
            scope.launch {
                keyboardController?.hide()
                try {
                    // ── Nominatim Geocoding (Prioritize Bhopal) ─────────────────────────
                    val refinedQuery = if (destinationText.lowercase().contains("bhopal")) {
                        destinationText
                    } else {
                        "$destinationText, Bhopal"
                    }
                    val query = java.net.URLEncoder.encode(refinedQuery, "UTF-8")
                    val geocodeUrl = "https://nominatim.openstreetmap.org/search?q=$query&format=json&limit=1&countrycodes=in"
                    
                    val target = withContext(Dispatchers.IO) {
                        try {
                            val conn = java.net.URL(geocodeUrl).openConnection() as java.net.HttpURLConnection
                            conn.setRequestProperty("User-Agent", "DualShieldAI/1.0")
                            val response = conn.inputStream.bufferedReader().readText()
                            val results = org.json.JSONArray(response)
                            if (results.length() > 0) {
                                val first = results.getJSONObject(0)
                                LatLng(first.getDouble("lat"), first.getDouble("lon"))
                            } else null
                        } catch (e: Exception) { null }
                    }

                    if (target != null) {
                        destinationLatLng = target
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))

                        if (userLat != null && userLng != null) {
                            withContext(Dispatchers.IO) {
                                try {
                                    // ── OSRM Routing (FREE Directions) ───────────────────────
                                    val urlString = "http://router.project-osrm.org/route/v1/driving/${userLng},${userLat};${target.longitude},${target.latitude}?overview=full&geometries=geojson"
                                    val conn = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
                                    conn.setRequestProperty("User-Agent", "DualShieldAI/1.0")
                                    val response = conn.inputStream.bufferedReader().readText()
                                    val json = org.json.JSONObject(response)
                                    val routes = json.getJSONArray("routes")
                                    if (routes.length() > 0) {
                                        val route = routes.getJSONObject(0)
                                        val geometry = route.getJSONObject("geometry")
                                        val coords = geometry.getJSONArray("coordinates")
                                        val points = mutableListOf<LatLng>()
                                        for (i in 0 until coords.length()) {
                                            val point = coords.getJSONArray(i)
                                            // OSRM returns [longitude, latitude]
                                            points.add(LatLng(point.getDouble(1), point.getDouble(0)))
                                        }
                                        routePoints = points
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { 
                                        android.widget.Toast.makeText(context, "Routing failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Location not found on OSM!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch(e:Exception){
                    android.widget.Toast.makeText(context, "Search failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(userLat, userLng) {
        if (userLat != null && userLng != null) {
            delay(3000L) // debounce
            try {
                // ── Nominatim Reverse Geocoding (FREE Location Name) ────────────────
                val reverseUrl = "https://nominatim.openstreetmap.org/reverse?lat=$userLat&lon=$userLng&format=json"
                val address = withContext(Dispatchers.IO) {
                    try {
                        val conn = java.net.URL(reverseUrl).openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("User-Agent", "DualShieldAI/1.0")
                        val response = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(response)
                        json.optString("display_name", "Current Location")
                    } catch (e: Exception) { null }
                }
                if (address != null) {
                    currentAddress = address
                }
            } catch (e: Exception) {}
        }
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val key = ai.metaData.getString("com.google.android.geo.API_KEY")
            if (key == null || key == "YOUR_API_KEY_HERE" || key.isBlank()) {
                apiKeyStatus = "⚠️ Placeholder Key"
                isGoogleMapsAvailable = false
            } else {
                apiKeyStatus = "✅ Google Maps Active"
                isGoogleMapsAvailable = true
            }
        } catch (e: Exception) { 
            apiKeyStatus = "❌ Key Error" 
            isGoogleMapsAvailable = false
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        userLat = loc.latitude; userLng = loc.longitude
                        gpsReady = true
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f)
                    }
                }
            } catch (e: SecurityException) { }
        }
    }

    DisposableEffect(hasLocationPermission) {
        val locationCallback = object : LocationCallback() {
            var isFirstUpdate = true
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                userLat = location.latitude; userLng = location.longitude
                speedKmh = (location.speed * 3.6).toInt().coerceAtLeast(0)
                gpsReady = true
                if (isFirstUpdate) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(location.latitude, location.longitude), 15f)
                    isFirstUpdate = false
                }
                if (!isGoogleMapsAvailable) {
                    webViewRef?.evaluateJavascript("updateLocation(${location.latitude}, ${location.longitude})", null)
                }
            }
        }
        if (hasLocationPermission) {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).build()
            try { fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper()) } catch (e: Exception) {}
        }
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val apiZones = api.getAccidentZones().body() ?: emptyList()
                val assetZones = loadRiskZonesFromAssets(context)
                // Deduplicate by name and coordinates
                zones = (apiZones + assetZones).distinctBy { "${it.name}${it.lat}${it.lng}" }
                zones.forEach { android.util.Log.d("MapScreen", "Loaded Zone: ${it.name}") }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Loaded ${zones.size} Accident Zones", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                zones = loadRiskZonesFromAssets(context)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Loaded ${zones.size} Zones (Assets Only)", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.team404.dualshield.RISK_STATUS_CHANGED") {
                    isHighRisk = intent.getBooleanExtra("isHighRisk", false)
                }
            }
        }
        val filter = IntentFilter("com.team404.dualshield.RISK_STATUS_CHANGED")
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Tactical Background Layer (Optional over map if desired, but map covers most)
        
        if (isGoogleMapsAvailable) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = true,
                    mapType = MapType.NORMAL,
                    mapStyleOptions = if (isSystemInDarkTheme() || MaterialTheme.colorScheme.background.toArgb() == sentinelDark.toArgb()) {
                        MapStyleOptions(MapTheme.DARK_JSON)
                    } else null
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = false)
            ) {
                destinationLatLng?.let { target ->
                    val destPosition = LatLng(target.latitude, target.longitude)
                    val destMarkerState = rememberMarkerState(position = destPosition)
                    Marker(state = destMarkerState, title = "Destination", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    if (routePoints != null && routePoints!!.isNotEmpty()) {
                        Polyline(points = routePoints!!, color = sentinelGlowBlue, width = 12f)
                    } else {
                        userLat?.let { uLat -> userLng?.let { uLng ->
                            Polyline(points = listOf(LatLng(uLat, uLng), destPosition), color = sentinelGlowGreen, width = 8f)
                        } }
                    }
                }
                zones.forEach { zone ->
                    key("${zone.lat},${zone.lng},${zone.name}") {
                        val zoneRisk = zone.risk ?: "Moderate"
                        val colorData = when (zoneRisk) {
                            "Very High", "Extremely High" -> Pair(Color(0x66FF003C), Color(0xFFFF003C))
                            "High" -> Pair(Color(0x66FF9800), Color(0xFFE65100))
                            "Moderate" -> Pair(Color(0x66FFEB3B), Color(0xFFFBC02D))
                            else -> Pair(Color(0x4464748B), Color(0xFF64748B))
                        }
                        
                        Circle(
                            center = LatLng(zone.lat, zone.lng),
                            radius = zone.radius.toDouble(),
                            fillColor = colorData.first,
                            strokeColor = colorData.second,
                            strokeWidth = 3f
                        )
                    }
                }
            }
        } else {
            val isDark = MaterialTheme.colorScheme.background.toArgb() == sentinelDark.toArgb()
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        val lat = userLat ?: 28.6139
                        val lng = userLng ?: 77.2090
                        val html = buildLeafletHtml(lat, lng, zones, isDark)
                        loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
                    }
                }
            )
        }

        // ── TOP RAPIDO-STYLE SEARCH CARD ─────────────────────────────────────────
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0x1F000000))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left vertical dots
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF3B82F6), CircleShape))
                        Box(modifier = Modifier.width(2.dp).height(38.dp).background(Color(0xFFE2E8F0)))
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF4444), CircleShape))
                    }
                    
                    // Right text fields
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Current Location box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = currentAddress ?: "Locating you...",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Destination text field wrapper
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            TextField(
                                value = destinationText,
                                onValueChange = { destinationText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                placeholder = { Text("Where are you going?", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearchDestination() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (destinationText.isNotEmpty()) {
                                            IconButton(onClick = { 
                                                destinationText = ""
                                                destinationLatLng = null
                                                routePoints = null
                                            }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        IconButton(onClick = { onSearchDestination() }) {
                                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF3B82F6))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!hasLocationPermission) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                SentinelCard(modifier = Modifier.padding(32.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, tint = sentinelRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Location Access Required", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Text("Grant location access to visualize your mission coordinates.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                            colors = ButtonDefaults.buttonColors(containerColor = sentinelBlue),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("ALLOW ACCESS", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // ── HUD (Sentinel Style) ──────────────────────────────────────────────────
        // --- NEW: Zone Visibility Aids ---
        // --- NEW: Zone Visibility Aids ---        // 2. Zone Finder Menu (FAB)
        var showZoneMenu by remember { mutableStateOf(false) }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                if (showZoneMenu) {
                    val quickLinks = zones.filter { 
                        listOf("Neelbad", "Ratibad", "Bhadbhada", "Karond").any { key -> 
                            it.name?.contains(key, ignoreCase = true) == true 
                        }
                    }
                    quickLinks.forEach { targetZone ->
                        ExtendedFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(targetZone.lat, targetZone.lng), 15f)
                                    )
                                    showZoneMenu = false
                                }
                            },
                            icon = { Icon(Icons.Default.Place, null) },
                            text = { Text(targetZone.name ?: "Zone") },
                            containerColor = Color(0xFF3B82F6),
                            contentColor = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                
            }
        }
        // --- END: Zone Visibility Aids ---

        MapHUD(
            speed = speedKmh,
            highRisk = isHighRisk,
            lat = userLat,
            lng = userLng,
            currentAddress = currentAddress,
            share = shareEnabled,
            onShareToggle = { shareEnabled = it },
            gpsReady = gpsReady,
            onLocate = {
                userLat?.let { lat -> userLng?.let { lng ->
                    if (isGoogleMapsAvailable) {
                        scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17f)) }
                    } else {
                        webViewRef?.evaluateJavascript("map.flyTo([$lat, $lng], 17)", null)
                    }
                } }
            }
        )
    }
}

@Composable
fun DiagnosticRow(label: String, status: String, color: Color) {
    Row(modifier = Modifier.width(200.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(status, color = color, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun MapHUD(speed: Int, highRisk: Boolean, lat: Double?, lng: Double?, currentAddress: String?, share: Boolean, onShareToggle: (Boolean) -> Unit, gpsReady: Boolean, onLocate: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // My Location Button (Google Maps Style)
        FloatingActionButton(
            onClick = onLocate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 150.dp) // Adjusted to be closer to the telemetry card
                .size(48.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = sentinelBlue,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) { 
            Icon(
                if (gpsReady) Icons.Default.MyLocation else Icons.Default.LocationSearching, 
                contentDescription = "Center on me",
                modifier = Modifier.size(24.dp)
            ) 
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).padding(bottom = 20.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1F000000))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Safety Telemetry", 
                                color = MaterialTheme.colorScheme.onSurface, 
                                fontSize = 16.sp, 
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (highRisk) "High Risk Zone" else "Safe Environment", 
                                color = if (highRisk) Color(0xFFEF4444) else Color(0xFF10B981), 
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           Text(
                               "$speed", 
                               color = MaterialTheme.colorScheme.onSurface, 
                               fontSize = 28.sp, 
                               fontWeight = FontWeight.Black
                           )
                           Text(
                               " km/h", 
                               color = MaterialTheme.colorScheme.onSurfaceVariant, 
                               fontSize = 12.sp, 
                               fontWeight = FontWeight.Bold,
                               modifier = Modifier.padding(top = 8.dp)
                           )
                        }
                    }
                }
            }
        }
    }
}

private fun buildLeafletHtml(lat: Double, lng: Double, zones: List<Zone>, isDark: Boolean): String {
    val zonesJson = zones.joinToString(",", "[", "]") { z ->
        """{"lat":${z.lat},"lng":${z.lng},"radius":${z.radius},"name":"${z.name}"}"""
    }
    
    val bgColor = if (isDark) "#0A0A0B" else "#FFFFFF"
    val tileFilter = if (isDark) "invert(100%) hue-rotate(180deg) brightness(95%) contrast(90%)" else "none"
    val markerColor = if (isDark) "#34D399" else "#3B82F6"

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            body { margin: 0; background: $bgColor; }
            #map { width: 100vw; height: 100vh; background: $bgColor; }
            .leaflet-tile-container { filter: $tileFilter; }
            .leaflet-container { background: $bgColor !important; }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([$lat, $lng], 15);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
            
            var userIcon = L.divIcon({
                className: 'custom-div-icon',
                html: "<div style='background-color:$markerColor;width:12px;height:12px;border-radius:50%;border:2px solid white;box-shadow:0 0 15px $markerColor;'></div>",
                iconSize: [12, 12], iconAnchor: [6, 6]
            });
            var marker = L.marker([$lat, $lng], {icon: userIcon}).addTo(map);
            
            var zonesData = $zonesJson;
            zonesData.forEach(function(z) {
                L.circle([z.lat, z.lng], {
                    radius: z.radius, color: '#EF4444', fillColor: '#EF4444', fillOpacity: 0.15, weight: 1
                }).addTo(map);
                
                // Add marker to show "marks" in fallback view
                L.marker([z.lat, z.lng]).addTo(map).bindPopup(z.name || "Risk Zone");
            });

            function updateLocation(lat, lng) {
                marker.setLatLng([lat, lng]);
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}

object MapTheme {
    val DARK_JSON = """
        [
          { "elementType": "geometry", "stylers": [ { "color": "#121212" } ] },
          { "elementType": "labels.text.fill", "stylers": [ { "color": "#757575" } ] },
          { "elementType": "labels.text.stroke", "stylers": [ { "color": "#212121" } ] },
          { "featureType": "road", "elementType": "geometry", "stylers": [ { "color": "#2c2c2c" } ] },
          { "featureType": "water", "elementType": "geometry", "stylers": [ { "color": "#000000" } ] }
        ]
    """.trimIndent()
}

private fun loadRiskZonesFromAssets(context: Context): List<Zone> {
    return try {
        val jsonString = context.assets.open("risk_zones.json").bufferedReader().use { it.readText() }
        val listType = object : com.google.gson.reflect.TypeToken<List<Zone>>() {}.type
        com.google.gson.Gson().fromJson(jsonString, listType) ?: emptyList()
    } catch (e: Exception) {
        android.util.Log.e("MapScreen", "Error loading risk zones", e)
        emptyList()
    }
}

private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng
        val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(p)
    }
    return poly
}
