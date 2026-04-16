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
    onNavigateToSensors: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Advanced Control", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TacticalGrid(gridColor = sentinelBlue.copy(alpha = 0.05f))
            
            AdvancedCard("📱 System Diagnostics", "Service status and permission registry", onNavigateToStatus)
            AdvancedCard("📊 Sensor Telemetry", "High-frequency accel/gyro stream", onNavigateToSensors)
            
        }
    }
}

@Composable
fun AdvancedCard(title: String, desc: String, onClick: () -> Unit) {
    SentinelCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = sentinelBlue.copy(alpha = 0.5f))
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("System Status", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            TacticalGrid(gridColor = sentinelBlue.copy(alpha = 0.05f))
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
                Text("SERVICE REGISTRY", color = sentinelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                SentinelCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        StatusRow("Background Monitor", isServiceRunning(com.team404.dualshield.services.SensorMonitoringService::class.java))
                        StatusRow("GPS Tracking Core", ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        StatusRow("Live AI Inference", true)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text("PERMISSIONS", color = sentinelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                SentinelCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        StatusRow("Precise Location", ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        StatusRow("Emergency SMS", ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
                        StatusRow("Voice Recording", ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        StatusRow("Direct Dialing", ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sensor Telemetry", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            TacticalGrid(gridColor = sentinelBlue.copy(alpha = 0.05f))
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("ACCELEROMETER (m/s²)", color = sentinelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(16.dp))
                SensorCard("X-Axis", accelX, "", sentinelBlue)
                SensorCard("Y-Axis", accelY, "", sentinelGreen)
                SensorCard("Z-Axis", accelZ, "", sentinelRed)
                
                Spacer(modifier = Modifier.height(32.dp))
                Text("GYROSCOPE (rad/s)", color = sentinelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(16.dp))
                SensorCard("Roll", gyroX, "", sentinelBlue)
                SensorCard("Pitch", gyroY, "", sentinelGreen)
                SensorCard("Yaw", gyroZ, "", sentinelRed)
            }
        }
    }
}

@Composable
fun SensorCard(label: String, value: Float, unit: String, color: Color) {
    SentinelCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("%.3f %s".format(value, unit), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = (value.coerceIn(-20f, 20f) + 20f) / 40f,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Incident History", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AccentBlueLight)
            } else if (incidents.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("System Integrity High", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("No incidents detected by AI Sentinel.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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

    SentinelCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(sentinelRed.copy(0.1f), CircleShape).border(1.dp, sentinelRed.copy(0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = sentinelRed, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Detected Collision", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("SEV ${item.severityLevel}", color = sentinelRed, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("%.3f, %.3f".format(item.latitude, item.longitude), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun StatusRow(label: String, active: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(6.dp).background(if (active) sentinelGreen else sentinelRed, CircleShape))
            Text(if (active) "ACTIVE" else "OFF", color = if (active) sentinelGreen else sentinelRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
