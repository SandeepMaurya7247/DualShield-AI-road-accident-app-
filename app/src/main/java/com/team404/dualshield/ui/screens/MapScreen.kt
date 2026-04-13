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
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bhopal, 12f)
    }

    val onSearchDestination = {
        if (destinationText.isNotBlank()) {
            scope.launch {
                keyboardController?.hide()
                try {
                    val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    val key = ai.metaData.getString("com.google.android.geo.API_KEY")
                    val query = java.net.URLEncoder.encode(destinationText, "UTF-8")
                    val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json?address=$query&key=$key"
                    var apiStatusMsg = ""
                    
                    val target = withContext(Dispatchers.IO) {
                        try {
                            val response = java.net.URL(geocodeUrl).readText()
                            val json = org.json.JSONObject(response)
                            if (json.has("status") && json.getString("status") != "OK") {
                                apiStatusMsg = json.optString("error_message", json.getString("status"))
                            }
                            val results = json.optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val loc = results.getJSONObject(0).getJSONObject("geometry").getJSONObject("location")
                                LatLng(loc.getDouble("lat"), loc.getDouble("lng"))
                            } else null
                        } catch (e: Exception) { 
                            apiStatusMsg = e.message ?: "Network error"
                            null 
                        }
                    }

                    if (target != null) {
                        destinationLatLng = target
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))

                        if (userLat != null && userLng != null) {
                           withContext(Dispatchers.IO) {
                               try {
                                   val urlString = "https://maps.googleapis.com/maps/api/directions/json?origin=${userLat},${userLng}&destination=${target.latitude},${target.longitude}&key=$key"
                                   val response = java.net.URL(urlString).readText()
                                   val json = org.json.JSONObject(response)
                                   val routes = json.optJSONArray("routes")
                                   if (routes != null && routes.length() > 0) {
                                       val polyline = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                                       routePoints = decodePolyline(polyline)
                                   } else {
                                       val dirStatus = json.optString("status", "UNKNOWN")
                                       val errMsg = json.optString("error_message", dirStatus)
                                       withContext(Dispatchers.Main) {
                                           android.widget.Toast.makeText(context, "Directions Error: $errMsg", android.widget.Toast.LENGTH_LONG).show()
                                       }
                                   }
                               } catch (e: Exception) {
                                   withContext(Dispatchers.Main) {
                                       android.widget.Toast.makeText(context, "Directions failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                   }
                               }
                           }
                        }
                    } else {
                        if (apiStatusMsg.isNotEmpty()) {
                            android.widget.Toast.makeText(context, "Google API Error: $apiStatusMsg", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Location completely not found!", android.widget.Toast.LENGTH_SHORT).show()
                        }
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
                val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                val key = ai.metaData.getString("com.google.android.geo.API_KEY")
                val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json?latlng=${userLat!!},${userLng!!}&key=$key"
                
                val address = withContext(Dispatchers.IO) {
                    try {
                        val response = java.net.URL(geocodeUrl).readText()
                        val json = org.json.JSONObject(response)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            results.getJSONObject(0).getString("formatted_address")
                        } else null
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
                zones = (apiZones + assetZones).distinctBy { "${it.lat},${it.lng}" }
            } catch (e: Exception) {
                zones = loadRiskZonesFromAssets(context)
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
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
            ) {
                userLat?.let { lat -> userLng?.let { lng ->
                    val userPosition = LatLng(lat, lng)
                    val userMarkerState = rememberMarkerState(position = userPosition)
                    Marker(state = userMarkerState, title = "You", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    
                    destinationLatLng?.let { target ->
                        val destPosition = LatLng(target.latitude, target.longitude)
                        val destMarkerState = rememberMarkerState(position = destPosition)
                        Marker(state = destMarkerState, title = "Destination", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        if (routePoints != null && routePoints!!.isNotEmpty()) {
                            Polyline(points = routePoints!!, color = sentinelGlowBlue, width = 12f)
                        } else {
                            Polyline(points = listOf(userPosition, destPosition), color = sentinelGlowGreen, width = 8f)
                        }
                    }
                } }
                zones.forEach { zone ->
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
                    
                    Marker(
                        state = rememberMarkerState(position = LatLng(zone.lat, zone.lng)),
                        title = zone.name ?: "Restricted Area",
                        snippet = "Risk Level: $zoneRisk",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            when (zoneRisk) {
                                "Very High", "Extremely High" -> BitmapDescriptorFactory.HUE_RED
                                "High" -> BitmapDescriptorFactory.HUE_ORANGE
                                "Moderate" -> BitmapDescriptorFactory.HUE_YELLOW
                                else -> BitmapDescriptorFactory.HUE_CYAN
                            }
                        )
                    )
                }
            }
        } else {
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
                        val html = buildLeafletHtml(lat, lng, zones)
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
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, lightBorder, RoundedCornerShape(16.dp))
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
                                .height(48.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = currentAddress ?: "Locating you...",
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Destination text field wrapper
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            TextField(
                                value = destinationText,
                                onValueChange = { destinationText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Where are you going?", color = Color(0xFF64748B), fontSize = 14.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearchDestination() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A)
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
        FloatingActionButton(
            onClick = onLocate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 180.dp).size(56.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF3B82F6),
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) { 
            Icon(if (gpsReady) Icons.Default.MyLocation else Icons.Default.LocationSearching, contentDescription = null) 
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).padding(bottom = 70.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1F000000))
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x0C000000), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Safety Telemetry", 
                                color = Color(0xFF0F172A), 
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
                               color = Color(0xFF0F172A), 
                               fontSize = 28.sp, 
                               fontWeight = FontWeight.Black
                           )
                           Text(
                               " km/h", 
                               color = Color(0xFF64748B), 
                               fontSize = 12.sp, 
                               fontWeight = FontWeight.Bold,
                               modifier = Modifier.padding(top = 8.dp)
                           )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x11000000)))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Share live location to Guardians", 
                            color = Color(0xFF64748B), 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = share, 
                            onCheckedChange = onShareToggle, 
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3B82F6))
                        )
                    }
                }
            }
        }
    }
}

private fun buildLeafletHtml(lat: Double, lng: Double, zones: List<Zone>): String {
    val zonesJson = zones.joinToString(",", "[", "]") { z ->
        """{"lat":${z.lat},"lng":${z.lng},"radius":${z.radius},"name":"${z.name}"}"""
    }
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            body { margin: 0; background: #FFFFFF; }
            #map { width: 100vw; height: 100vh; }
            /* Light themed map filtering */
            .leaflet-tile { }
            .leaflet-container { background-color: #FFFFFF !important; }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([$lat, $lng], 15);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
            
            var userIcon = L.divIcon({
                className: 'custom-div-icon',
                html: "<div style='background-color:#3B82F6;width:12px;height:12px;border-radius:50%;border:2px solid white;box-shadow:0 0 15px #3B82F6;'></div>",
                iconSize: [12, 12], iconAnchor: [6, 6]
            });
            var marker = L.marker([$lat, $lng], {icon: userIcon}).addTo(map);
            
            var zones = $zonesJson;
            zones.forEach(function(z) {
                L.circle([z.lat, z.lng], {
                    radius: z.radius, color: '#EF4444', fillColor: '#EF4444', fillOpacity: 0.15, weight: 1
                }).addTo(map);
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
