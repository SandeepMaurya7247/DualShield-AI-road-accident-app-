package com.team404.dualshield.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.team404.dualshield.ai.SpeechAssistantManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    userPhone: String = "",
    userName: String = "User",
    onNavigateToCountdown: () -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val api = remember { BackendApi.create() }
    val scope = rememberCoroutineScope()
    var backendOnline by remember { mutableStateOf<Boolean?>(null) }
    var incidentCount by remember { mutableStateOf("—") }
    
    // AI Assistant Readiness
    var aiReady by remember { mutableStateOf(false) }
    val assistantManager = remember {
        SpeechAssistantManager(
            context = context,
            onReady = { aiReady = true },
            onCommandDetected = { }
        )
    }

    // Live GPS state
    var gpsLat by remember { mutableStateOf<Double?>(null) }
    var gpsLng by remember { mutableStateOf<Double?>(null) }
    var gpsSpeed by remember { mutableStateOf(0) }
    var gpsAccuracy by remember { mutableStateOf("—") }
    var locationShared by remember { mutableStateOf(false) }
    var clipboardMsg by remember { mutableStateOf("") }
    var zones by remember { mutableStateOf<List<com.team404.dualshield.api.Zone>>(emptyList()) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(23.2599, 77.4126), 15f)
    }

    // GPS Listener
    DisposableEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                gpsLat = loc.latitude
                gpsLng = loc.longitude
                gpsSpeed = (loc.speed * 3.6).toInt().coerceAtLeast(0)
                gpsAccuracy = if (loc.accuracy < 10) "High" else if (loc.accuracy < 30) "Medium" else "Low"
            }
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, listener)
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, listener)
        } catch (e: SecurityException) { }
        onDispose { 
            lm.removeUpdates(listener)
            assistantManager.stop()
        }
    }

    // Auto-follow logic for Mini-Map (Safe lifecycle-aware approach)
    LaunchedEffect(gpsLat, gpsLng) {
        if (gpsLat != null && gpsLng != null) {
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(gpsLat!!, gpsLng!!), 16f)
                )
            } catch (e: Exception) {
                // Silently fail if map isn't ready
            }
        }
    }

    var incidentList by remember { mutableStateOf<List<com.team404.dualshield.api.IncidentItem>>(emptyList()) }

    // Fetch backend health + incident count on launch
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val health = api.healthCheck()
                backendOnline = health.isSuccessful && health.body()?.status == "online"
            } catch (e: Exception) { backendOnline = false }
        }
        scope.launch {
            try {
                val resp = api.getIncidents()
                val items = resp.body() ?: emptyList()
                incidentList = items
                incidentCount = items.size.toString()
            } catch (e: Exception) { incidentCount = "0" }
        }
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

    // Animation for Protection Ring
    val infiniteTransition = rememberInfiniteTransition(label = "protection")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "ringAlpha"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringScale"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(lightBackground)
    ) {
        // Tactical Background Layer
        TacticalGrid()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = AccentBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("DualShield AI", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = (-0.5).sp)
                        if (userName.isNotBlank() && userName != "User")
                            Text("Guardian Active • $userName", color = AccentBlueLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                BackendStatusChip(backendOnline)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Glowing Sentinel Mission Core ──────────────────────────────
            Box(
                modifier = Modifier.size(220.dp).clickable { onNavigateToCountdown() },
                contentAlignment = Alignment.Center
            ) {
                // Tactical Rotating Layers
                RotatingStatusRing(modifier = Modifier.size(210.dp), baseColor = sentinelGreen)
                RotatingStatusRing(modifier = Modifier.size(170.dp), baseColor = sentinelBlue, layerCount = 2)
                
                // Central Core Surface
                Surface(
                    modifier = Modifier.size(130.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        Brush.linearGradient(listOf(sentinelBlue, sentinelGreen))
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Security, 
                            contentDescription = null, 
                            tint = sentinelGreen, 
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "SENTINEL", 
                            color = sentinelGreen, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Black, 
                            letterSpacing = 2.sp
                        )
                        Text(
                            "ACTIVE", 
                            color = MaterialTheme.colorScheme.onBackground, 
                            fontSize = 22.sp, 
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // AI Voice Sentinel Status
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (aiReady) AccentGreenDark.copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (aiReady) AccentGreen.copy(0.3f) else Color.Transparent, RoundedCornerShape(100.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (aiReady) Icons.Default.GraphicEq else Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = if (aiReady) AccentGreenBright else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (aiReady) "VOICE SENTINEL READY" else "INITIALIZING AI...",
                    color = if (aiReady) AccentGreenBright else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status Grid
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCard(
                    Modifier.weight(1f), 
                    Icons.Default.Speed, 
                    "SPEED", 
                    gpsSpeed.toString(), 
                    "km/h", 
                    null,
                    labelBadge = if (gpsSpeed > 0) "MOVING" else "STATIONARY"
                )
                StatusCard(
                    Modifier.weight(1f), 
                    Icons.Default.Sensors, 
                    "SENSORS", 
                    if (aiReady) "ACTIVE\nSCANNING" else "CORE\nBOOTING", 
                    "", 
                    if (aiReady) sentinelGlowGreen else sentinelGlowRed,
                    labelBadge = if (aiReady) "READY" else "SYNC"
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Live Location Card (Polished)
            LiveLocationCard(
                lat = gpsLat,
                lng = gpsLng,
                cameraPositionState = cameraPositionState,
                speed = gpsSpeed,
                accuracy = gpsAccuracy,
                isSharing = locationShared,
                onShareToggle = { locationShared = it },
                zones = zones,
                onCopyLink = {
                    val lat = gpsLat ?: 28.6139
                    val lng = gpsLng ?: 77.2090
                    val link = "https://maps.google.com/?q=$lat,$lng"
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Location", link))
                    clipboardMsg = "Location link copied!"
                }
            )
            
            if (clipboardMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(clipboardMsg, color = AccentGreen, fontSize = 12.sp)
                LaunchedEffect(clipboardMsg) {
                    delay(2500)
                    clipboardMsg = ""
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Incidents (Glassmorphic)
            SentinelCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("MISSION LOGS", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        TextButton(onClick = onNavigateToHistory) {
                            Text("HISTORY", color = sentinelGlowBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (incidentList.isEmpty()) {
                        InsightItem(true, "Autonomous Vigilance", "Everything is clear, Guardian.")
                        Spacer(modifier = Modifier.height(16.dp))
                        InsightItem(false, "GPS Precision", "High-fidelity lock maintained.")
                    } else {
                        incidentList.take(3).forEachIndexed { i, item ->
                            InsightItem(i == 0, "Incident #${item.userId.take(4)}", "Severity Level ${item.severityLevel} • ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))}")
                            if (i < minOf(incidentList.size - 1, 2)) Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Final Action
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(0.9f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = lightCard),
                shape = RoundedCornerShape(100.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, lightBorder.copy(0.1f)),
                elevation = ButtonDefaults.buttonElevation(2.dp)
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = null, tint = AlertRedBright)
                Spacer(modifier = Modifier.width(10.dp))
                Text("PAUSE SENTINEL PROTECTION", fontWeight = FontWeight.Black, color = AlertRedBright, fontSize = 13.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BackendStatusChip(online: Boolean?) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(sentinelBlue.copy(0.05f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val dotColor = when (online) {
            true -> AccentGreen
            false -> AlertRed
            else -> Color.Gray
        }
        Box(modifier = Modifier.size(7.dp).background(dotColor, CircleShape))
        Text(
            text = when (online) { true -> "CLOUD LIVE"; false -> "LOCAL ONLY"; else -> "SYNCING..." },
            color = dotColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun StatusCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    dotColor: Color?,
    labelBadge: String? = null
) {
    Card(
        modifier = modifier.height(130.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, lightBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp, pressedElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = sentinelBlue, modifier = Modifier.size(20.dp))
                    if (labelBadge != null) {
                        Box(modifier = Modifier.background(sentinelBlue.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(labelBadge, color = sentinelBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    } else if (dotColor != null) {
                        Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
                    }
                }
                Column {
                    Text(title, color = lightTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (unit.isEmpty()) {
                            Text(value, color = lightTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                        } else {
                            Text(value, color = lightTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text(" $unit", color = lightTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightItem(isPrimary: Boolean, title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .background(if (isPrimary) AccentGreenBright else MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(100))
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column {
            Text(title, color = if (isPrimary) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
fun LiveLocationCard(
    lat: Double?,
    lng: Double?,
    cameraPositionState: CameraPositionState,
    speed: Int,
    accuracy: String,
    isSharing: Boolean,
    onShareToggle: (Boolean) -> Unit,
    onCopyLink: () -> Unit,
    zones: List<com.team404.dualshield.api.Zone> = emptyList()
) {
    val hasGps = lat != null && lng != null

    SentinelCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            if (hasGps) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = MapType.NORMAL),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false, // We'll add our own custom one
                        scrollGesturesEnabled = true,
                        zoomGesturesEnabled = true,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false
                    )
                ) {
                    Marker(
                        state = rememberMarkerState(position = LatLng(lat!!, lng!!)),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                    
                    // Render High-Risk Zones only when zoomed in
                    if (cameraPositionState.position.zoom > 12.5f) {
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
                                strokeWidth = 2f
                            )
                            Marker(
                                state = rememberMarkerState(position = LatLng(zone.lat, zone.lng)),
                                title = zone.name ?: "Restricted Area",
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    if (zoneRisk == "Moderate") BitmapDescriptorFactory.HUE_YELLOW 
                                    else BitmapDescriptorFactory.HUE_RED
                                ),
                                alpha = 0.7f
                            )
                        }
                    }
                }

                // HUD Overlay: Bottom Gradient for text readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(0.8f))
                            )
                        )
                )

                // HUD Overlay: "LIVE SURVEILLANCE ACTIVE"
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(sentinelGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "LIVE SURVEILLANCE ACTIVE",
                        color = lightTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // HUD Overlay: My Location Button (Custom)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .clickable { 
                            // Re-center camera on user
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(0.9f),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Color.Black.copy(0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = sentinelGlowBlue, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("BOOTING SURVEILLANCE SYSTEM...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

private fun loadRiskZonesFromAssets(context: Context): List<com.team404.dualshield.api.Zone> {
    return try {
        val jsonString = context.assets.open("risk_zones.json").bufferedReader().use { it.readText() }
        val listType = object : com.google.gson.reflect.TypeToken<List<com.team404.dualshield.api.Zone>>() {}.type
        com.google.gson.Gson().fromJson(jsonString, listType) ?: emptyList()
    } catch (e: Exception) {
        android.util.Log.e("HomeScreen", "Error loading risk zones", e)
        emptyList()
    }
}
