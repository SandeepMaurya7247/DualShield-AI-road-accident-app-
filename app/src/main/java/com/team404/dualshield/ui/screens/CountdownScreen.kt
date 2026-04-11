package com.team404.dualshield.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
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
import com.team404.dualshield.ai.SpeechAssistantManager
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.api.ContactItem
import com.team404.dualshield.api.IncidentReport
import com.team404.dualshield.ui.theme.*
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
    var sosDispatched by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var aiStatus by remember { mutableStateOf("Initializing AI...") }
    var audioPermissionGranted by remember { mutableStateOf(false) }

    // AI Assistant Manager
    val assistantManager = remember {
        SpeechAssistantManager(
            context = context,
            onReady = {
                if (audioPermissionGranted) {
                    aiStatus = "Listening for 'Yes'..."
                    Log.d("CountdownScreen", "AI Ready")
                }
            },
            onCommandDetected = { command ->
                if (command == "CANCEL") {
                    scope.launch { onCancel() }
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        audioPermissionGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (audioPermissionGranted) {
            scope.launch {
                delay(800)
                assistantManager.askSafetyQuery()
                assistantManager.startListening()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (userPhone.isNotEmpty()) {
            Log.d("CountdownScreen", "Fetching contacts for user: $userPhone")
            try {
                val response = api.getContacts(userPhone)
                if (response.isSuccessful) {
                    contacts = response.body() ?: emptyList()
                    Log.d("CountdownScreen", "Found ${contacts.size} emergency contacts")
                } else {
                    Log.e("CountdownScreen", "Failed to fetch contacts: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("CountdownScreen", "Error fetching contacts: ${e.message}")
            }
        } else {
            Log.w("CountdownScreen", "No user phone provided, cannot fetch contacts!")
        }
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
        if (!sosDispatched) {
            sosDispatched = true
            scope.launch {
                aiStatus = "Dispatching Emergency Alerts..."
                val (lat, lng) = getLiveLocation()
                
                // 1. Report to Backend
                try {
                    api.reportIncident(IncidentReport(userId, lat, lng, 3, System.currentTimeMillis()))
                } catch (e: Exception) {}

                // 2. Send SMS to ALL primary contacts
                try {
                    val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }

                    val message = "DANGER! DualShield AI detected an accident. Location: https://www.google.com/maps?q=$lat,$lng"
                    
                    if (contacts.isEmpty()) {
                        Log.w("SOS_Dispatch", "No contacts found to notify!")
                        aiStatus = "No Contacts Found! Calling in 5s..."
                    } else {
                        contacts.forEach { contact ->
                            if (contact.contact_phone.isNotEmpty()) {
                                smsManager?.sendTextMessage(contact.contact_phone, null, message, null, null)
                                Log.d("SOS_Dispatch", "SMS sent to ${contact.contact_phone}")
                            }
                        }
                        aiStatus = "SMS Sent to ${contacts.size} Relatives. Calling in 5s..."
                    }
                } catch (e: Exception) {
                    Log.e("SOS_Dispatch", "SMS failed: ${e.message}")
                }

                // 3. Wait for 5 seconds as requested by user
                delay(5000L)

                // 4. Trigger automated Phone Call to Primary Contact
                val primaryContact = contacts.firstOrNull()
                if (primaryContact != null) {
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:${primaryContact.contact_phone}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { 
                        context.startActivity(callIntent) 
                        Log.d("SOS_Dispatch", "Initiating Call to ${primaryContact.contact_phone}")
                    } catch (e: Exception) {
                        Log.e("SOS_Dispatch", "Call failed: ${e.message}")
                    }
                }

                assistantManager.stop()
                onTimeUp()
            }
        }
    }

    LaunchedEffect(timeLeft) {
        if (timeLeft > 0) {
            delay(1000L)
            timeLeft -= 1
        } else {
            dispatchSos()
        }
    }

    DisposableEffect(Unit) {
        onDispose { assistantManager.stop() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(sentinelBlack)
    ) {
        // Tactical Background Layer
        TacticalGrid(gridColor = sentinelRed.copy(alpha = 0.05f))

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulse Glow Header
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
                Text("SENTINEL AI: $aiStatus", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .background(sentinelRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(1.dp, sentinelRed, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    "CRITICAL IMPACT ALERT", 
                    color = sentinelGlowRed, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Black, 
                    letterSpacing = 2.sp
                )
            }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("CRASH\nDETECTED!", color = TextWhite, fontSize = 42.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 48.sp)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Assistant is asking: \"Are you safe?\"", color = TextGray, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = timeLeft / 30f, modifier = Modifier.fillMaxSize(), color = AlertRedBright, trackColor = Color.Black.copy(0.3f), strokeWidth = 8.dp)
            Text(text = "$timeLeft", color = TextWhite, fontSize = 80.sp, fontWeight = FontWeight.Light)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        SentinelCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SAY \"YES\" TO CANCEL PROTOCOL", color = sentinelGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(if (sosDispatched) "SOS DISPATCHED" else "Emergency signal in $timeLeft seconds", color = TextGray, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { dispatchSos() },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(if (sosDispatched) "STAY CALM" else "SEND SOS NOW", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f)),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text("CANCEL SOS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        }
    }
}
