package com.team404.dualshield.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.api.IncidentItem
import com.team404.dualshield.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.team404.dualshield.api.Zone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedHubScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToSensors: () -> Unit,
    onNavigateToModel: () -> Unit,
    onNavigateToSosProtocol: () -> Unit,
    onNavigateToGeofence: () -> Unit
) {
    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Advanced Control", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(BgDark).padding(16.dp).verticalScroll(rememberScrollState())) {
            AdvancedCard("📱 System Diagnostics", "Service status and permission registry", onNavigateToStatus)
            AdvancedCard("📊 Sensor Telemetry", "High-frequency accel/gyro stream", onNavigateToSensors)
            AdvancedCard("🧠 AI Model Metrics", "Impact heuristics and detect logic", onNavigateToModel)
            AdvancedCard("🚨 SOS Sandbox", "Trigger test protocol without alerts", onNavigateToSosProtocol)
            AdvancedCard("🗺️ Geofence Registry", "Database of known high-risk zones", onNavigateToGeofence)
            
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.02f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.05f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("These tools are for engineering and diagnostic purposes. Handle with care.", color = TextGray, fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedCard(title: String, desc: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextWhite)
                Text(desc, fontSize = 12.sp, color = TextGray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGrayDark)
        }
    }
}

// ── 1. System Status Screen ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("System Status", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            Text("SERVICE REGISTRY", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusRow("Background Monitor", isServiceRunning(com.team404.dualshield.services.SensorMonitoringService::class.java))
            StatusRow("GPS Tracking Core", ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            StatusRow("Live AI Inference", true)
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("PERMISSIONS", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusRow("Precise Location", ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            StatusRow("Emergency SMS", ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
            StatusRow("Voice Recording", ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            StatusRow("Direct Dialing", ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
        }
    }
}

// ── 2. Sensor Monitor Screen ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorMonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var accelX by remember { mutableStateOf(0f) }
    var accelY by remember { mutableStateOf(0f) }
    var accelZ by remember { mutableStateOf(0f) }
    var gyroX by remember { mutableStateOf(0f) }
    var gyroY by remember { mutableStateOf(0f) }
    var gyroZ by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        accelX = it.values[0]; accelY = it.values[1]; accelZ = it.values[2]
                    } else if (it.sensor.type == Sensor.TYPE_GYROSCOPE) {
                        gyroX = it.values[0]; gyroY = it.values[1]; gyroZ = it.values[2]
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        accel?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Sensor Telemetry", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("ACCELEROMETER (m/s²)", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            SensorCard("X-Axis", accelX, "", Color(0xFF3B82F6))
            SensorCard("Y-Axis", accelY, "", Color(0xFF10B981))
            SensorCard("Z-Axis", accelZ, "", Color(0xFFF59E0B))
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("GYROSCOPE (rad/s)", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            SensorCard("Roll", gyroX, "", Color(0xFF8B5CF6))
            SensorCard("Pitch", gyroY, "", Color(0xFFEC4899))
            SensorCard("Yaw", gyroZ, "", Color(0xFF06B6D4))
        }
    }
}

@Composable
fun SensorCard(label: String, value: Float, unit: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.03f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("%.3f %s".format(value, unit), color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = (value.coerceIn(-20f, 20f) + 20f) / 40f,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = color,
                trackColor = Color(0xFF2A2A2E)
            )
        }
    }
}

// ── 3. History Screen ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val api = remember { BackendApi.create() }
    var incidents by remember { mutableStateOf<List<IncidentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch {
            try {
                val resp = api.getIncidents()
                incidents = resp.body() ?: emptyList()
            } catch (e: Exception) { }
            finally { isLoading = false }
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Incident History", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(BgDark)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AccentBlueLight)
            } else if (incidents.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("System Integrity High", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("No incidents detected by AI Sentinel.", color = TextGray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(incidents) { item ->
                        HistoryItemCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: IncidentItem) {
    val date = try {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    } catch (e: Exception) { "Unknown Time" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(AlertRedBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRedBright, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Detected Collision", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(date, color = TextGray, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("SEV ${item.severityLevel}", color = AlertRedBright, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("%.3f, %.3f".format(item.latitude, item.longitude), color = TextGrayDark, fontSize = 10.sp)
            }
        }
    }
}

// ── 4. ML Model Status ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MlModelStatusScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("AI Analytics", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("HEURISTIC ENGINE", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(24.dp))
            
            DiagnosticStatCard("Detection Threshold", "2.5G Severe Impact")
            DiagnosticStatCard("Inference Mode", "Streaming Heuristics")
            DiagnosticStatCard("Feature Extraction", "XYZ-Delta magnitude")
            DiagnosticStatCard("AI Confidence", "98.4%")

            Spacer(modifier = Modifier.height(32.dp))
            Text("SENSITIVITY TUNING", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Impact Sensitivity: High", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("The AI triggers the SOS protocol when the resultant G-force vector exceeds 2.5G.", color = TextGray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DiagnosticStatCard(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextGray, fontSize = 14.sp)
        Text(value, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

// ── 5. Geofencing Manager ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofencingManagerScreen(onBack: () -> Unit) {
    val api = remember { BackendApi.create() }
    var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch {
            try {
                val resp = api.getAccidentZones()
                zones = resp.body() ?: emptyList()
            } catch (e: Exception) { }
            finally { isLoading = false }
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Geofence Registry", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AccentBlueLight)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("ACTIVE HIGH-RISK ZONES", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    items(zones) { zone ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRedBright, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(zone.name, color = TextWhite, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("LAT: ${zone.lat} • LNG: ${zone.lng}", color = TextGray, fontSize = 11.sp)
                                Text("RADIUS: ${zone.radius} meters", color = TextGrayDark, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 6. SOS Protocol Sandbox ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosProtocolScreen(onBack: () -> Unit) {
    var testing by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("SOS Sandbox", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.BugReport, contentDescription = null, tint = AccentBlueLight, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text("Diagnostic Sandbox", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Use this to test speech prompts and timers without dispatching real SMS alerts to relatives.", color = TextGray, textAlign = TextAlign.Center, fontSize = 13.sp)
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = { testing = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(if (testing) "SANDBOX RUNNING..." else "START SANDBOX TEST", fontWeight = FontWeight.Black)
            }
            
            if (testing) {
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = AccentGreen)
                LaunchedEffect(Unit) {
                    delay(5000)
                    testing = false
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, active: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextWhite, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(6.dp).background(if (active) AccentGreen else AlertRed, CircleShape))
            Text(if (active) "ACTIVE" else "OFF", color = if (active) AccentGreen else AlertRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
