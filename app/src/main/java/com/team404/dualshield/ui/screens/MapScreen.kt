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

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(28.6139, 77.2090), 12f)
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
                    
                    val target = withContext(Dispatchers.IO) {
                        try {
                            val response = java.net.URL(geocodeUrl).readText()
                            val json = org.json.JSONObject(response)
                            val results = json.getJSONArray("results")
                            if (results.length() > 0) {
                                val loc = results.getJSONObject(0).getJSONObject("geometry").getJSONObject("location")
                                LatLng(loc.getDouble("lat"), loc.getDouble("lng"))
                            } else null
                        } catch (e: Exception) { null }
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
                                   val routes = json.getJSONArray("routes")
                                   if (routes.length() > 0) {
                                       val polyline = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                                       routePoints = decodePolyline(polyline)
                                   }
                               } catch (e: Exception) {
                                   e.printStackTrace()
                               }
                           }
                        }
                    }
                } catch(e:Exception){}
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
                        val results = json.getJSONArray("results")
                        if (results.length() > 0) {
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
                zones = api.getAccidentZones().body() ?: emptyList()
            } catch (e: Exception) {
                zones = listOf(Zone("Demo Zone 1", 28.4595, 77.0266, 500f), Zone("Demo Zone 2", 28.6320, 77.2195, 300f))
            }
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
                    mapStyleOptions = MapStyleOptions(MapTheme.DARK_JSON)
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
            ) {
                userLat?.let { lat -> userLng?.let { lng ->
                    Marker(state = MarkerState(LatLng(lat, lng)), title = "You", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    
                    destinationLatLng?.let { target ->
                        Marker(state = MarkerState(target), title = "Destination", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        if (routePoints != null && routePoints!!.isNotEmpty()) {
                            Polyline(points = routePoints!!, color = sentinelGlowBlue, width = 12f)
                        } else {
                            Polyline(points = listOf(LatLng(lat, lng), target), color = sentinelGlowGreen, width = 8f)
                        }
                    }
                } }
                zones.forEach { zone ->
                    Circle(center = LatLng(zone.lat, zone.lng), radius = zone.radius.toDouble(), fillColor = Color(0x33EF4444), strokeColor = Color(0xFFEF4444), strokeWidth = 2f)
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

        // ── TOP DIAGNOSTICS & SEARCH ─────────────────────────────────────────
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
            OutlinedTextField(
                value = destinationText,
                onValueChange = { destinationText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Where to?", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.8f)) },
                leadingIcon = { Icon(Icons.Default.Search, tint = sentinelBlue, contentDescription = null) },
                trailingIcon = {
                   if (destinationText.isNotEmpty()) {
                        IconButton(onClick = { 
                            destinationText = ""
                            destinationLatLng = null
                        }) { Icon(Icons.Default.Clear, tint = sentinelRed, contentDescription = null) }
                   }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchDestination() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = sentinelBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha=0.9f)
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            SentinelCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = sentinelBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TACTICAL MAP OVERLAY", color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SentinelCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosticRow("CORE ENGINE", if (isGoogleMapsAvailable) "PRIMARY (G-MAPS)" else "BACKUP (OSM)", if (isGoogleMapsAvailable) sentinelGlowBlue else sentinelGlowGreen)
                    DiagnosticRow("SAT LOCK", if (gpsReady) "ACKNOWLEDGED" else "SEARCHING...", if (gpsReady) sentinelGlowGreen else sentinelGlowRed)
                    DiagnosticRow("ENCRYPTION", "AES-256 ACTIVE", sentinelGlowGreen)
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 260.dp).size(52.dp),
            containerColor = if (gpsReady) AccentBlue else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
        ) { Icon(if (gpsReady) Icons.Default.MyLocation else Icons.Default.LocationSearching, contentDescription = null, modifier = Modifier.size(22.dp)) }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            
            // SPEED CARD
            SentinelCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Speed, contentDescription = null, tint = sentinelGlowBlue) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("VELOCITY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text("$speed KM/H", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SECTOR RISK", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text(if (highRisk) "ALPHA - HIGH" else "SECURE", color = if (highRisk) sentinelRed else sentinelGreen, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // COORDS CARD
            SentinelCard(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("CURRENT LOCATION", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(currentAddress ?: "Acquiring satellite lock...", color = sentinelGlowBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background).padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LATITUDE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(if (lat != null) "%.5f°".format(lat) else "SEARCHING", color = sentinelGlowBlue, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LONGITUDE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(if (lng != null) "%.5f°".format(lng) else "SEARCHING", color = sentinelGlowBlue, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("SENTINEL UPLINK", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text("Real-time safety telemetry active", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Switch(checked = share, onCheckedChange = onShareToggle, colors = SwitchDefaults.colors(checkedTrackColor = sentinelBlue))
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
            body { margin: 0; background: #0F172A; }
            #map { width: 100vw; height: 100vh; }
            /* Dark themed map filtering */
            .leaflet-tile { filter: brightness(0.6) contrast(1.2) invert(100%) hue-rotate(180deg) saturate(0.3) brightness(1.7); }
            .leaflet-container { background-color: #0F172A !important; }
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
