package com.team404.dualshield.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team404.dualshield.ai.SpeechAssistantManager
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.api.ContactItem
import com.team404.dualshield.api.IncidentReport
import com.team404.dualshield.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun CountdownScreen(
    userId: String = "user_local",
    userPhone: String = "",
    onCancel: () -> Unit,
    onTimeUp: () -> Unit
) {
    val context = LocalContext.current
    val api = remember { BackendApi.create() }
    val scope = rememberCoroutineScope()
    
    var timeLeft by remember { mutableStateOf(30) }
    var countdownActive by remember { mutableStateOf(true) }
    var sosDispatched by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var aiStatus by remember { mutableStateOf("Initializing AI...") }
    var audioPermissionGranted by remember { mutableStateOf(false) }
    
    var dispatchJob by remember { mutableStateOf<Job?>(null) }

    fun performCancel() {
        Log.w("CountdownScreen", "❌ USER CANCELLED SOS")
        countdownActive = false
        dispatchJob?.cancel()
        
        // Notify service to reset its trigger flag immediately
        val intent = Intent("com.team404.dualshield.ACTION_SOS_CANCELLED")
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        
        com.team404.dualshield.emergency.EmergencyManager.resetSosStatus()
        onCancel()
    }

    val assistantManager = remember {
        SpeechAssistantManager(
            context = context,
            onReady = {
                if (audioPermissionGranted && countdownActive) {
                    aiStatus = "Listening for 'Yes'..."
                }
            },
            onCommandDetected = { command ->
                if (command == "CANCEL") {
                    Log.w("CountdownScreen", "🤖 Voice command: CANCEL")
                    performCancel()
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        audioPermissionGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (audioPermissionGranted && countdownActive) {
            scope.launch {
                delay(1500)
                if (countdownActive) {
                    assistantManager.askSafetyQuery()
                    assistantManager.startListening()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (userPhone.isNotEmpty()) {
            try {
                val response = api.getContacts(userPhone)
                if (response.isSuccessful) {
                    contacts = response.body() ?: emptyList()
                } else {
                    val localJson = com.team404.dualshield.api.UserSession.getContactsLocal(context)
                    val type = object : com.google.gson.reflect.TypeToken<List<ContactItem>>() {}.type
                    contacts = com.google.gson.Gson().fromJson(localJson, type) ?: emptyList()
                }
            } catch (e: Exception) {
                val localJson = com.team404.dualshield.api.UserSession.getContactsLocal(context)
                val type = object : com.google.gson.reflect.TypeToken<List<ContactItem>>() {}.type
                contacts = com.google.gson.Gson().fromJson(localJson, type) ?: emptyList()
            }
        }
        delay(300)
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS))
    }

    fun getLiveLocation(): Pair<Double, Double> {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) return Pair(loc.latitude, loc.longitude)
            }
            Pair(28.6139, 77.2090)
        } catch (e: Exception) { Pair(28.6139, 77.2090) }
    }

    fun dispatchSos() {
        if (sosDispatched) return  // Only check if already dispatched, nothing else
        sosDispatched = true
        Log.d("CountdownScreen", "⏱️ 30 seconds over. Dispatching SOS now!")
        
        dispatchJob = scope.launch {
            aiStatus = "SOS Sent! Calling Guardian..."

            val contactPhones = contacts.map { it.contact_phone.trim() }.filter { it.isNotEmpty() }

            Log.d("CountdownScreen", "Contacts found: ${contactPhones.size} → $contactPhones")
            if (contactPhones.isEmpty()) {
                Log.w("CountdownScreen", "⚠️ No emergency contacts! Calling 112 as fallback.")
            }

            val emergencyManager = com.team404.dualshield.emergency.EmergencyManager(context)
            emergencyManager.dispatchSOS(
                contactPhones = contactPhones,
                userId = userId,
                isAutomatic = false
            )

            assistantManager.stop()
            onTimeUp()
        }
    }

    // Reliable countdown using a single LaunchedEffect
    LaunchedEffect(Unit) {
        while (countdownActive && timeLeft > 0) {
            delay(1000L)
            timeLeft -= 1
        }
        if (countdownActive) {
            dispatchSos()
        }
    }

    DisposableEffect(Unit) {
        onDispose { 
            assistantManager.stop() 
            dispatchJob?.cancel()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        TacticalGrid(gridColor = sentinelRed.copy(alpha = 0.05f))

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Voice Status
            Row(
                modifier = Modifier
                    .background(sentinelRed.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, sentinelRed.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic, 
                    contentDescription = null, 
                    tint = if (audioPermissionGranted) sentinelGreen else Color.Gray, 
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("SENTINEL AI: $aiStatus", color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .background(sentinelRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(1.dp, sentinelRed, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("CRITICAL IMPACT ALERT", color = sentinelGlowRed, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        
            Spacer(modifier = Modifier.height(24.dp))
            Text("CRASH DETECTED!", color = MaterialTheme.colorScheme.onBackground, fontSize = 38.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 44.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = timeLeft / 30f, modifier = Modifier.fillMaxSize(), color = AlertRedBright, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 8.dp)
                Text(text = "$timeLeft", color = MaterialTheme.colorScheme.onBackground, fontSize = 70.sp, fontWeight = FontWeight.Light)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            SentinelCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SAY \"YES\" TO CANCEL", color = sentinelGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("Emergency signal in $timeLeft seconds", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { dispatchSos() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(if (sosDispatched) "SENDING..." else "SEND SOS NOW", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { performCancel() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("CANCEL SOS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}