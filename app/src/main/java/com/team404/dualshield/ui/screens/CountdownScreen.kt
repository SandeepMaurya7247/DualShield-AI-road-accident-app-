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

    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        audioPermissionGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        smsPermissionGranted = perms[Manifest.permission.SEND_SMS] == true
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
                val currentSmsPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED

                if (!currentSmsPermission) {
                    Log.e("SOS_Dispatch", "SEND_SMS permission not granted! Cannot send SMS.")
                    aiStatus = "SMS Permission Denied! Check app settings."
                } else {
                    try {
                        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(SmsManager::class.java)
                                ?: run {
                                    Log.e("SOS_Dispatch", "SmsManager is null on API 31+")
                                    null
                                } ?: return@launch
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getDefault()
                        }

                        val message = "EMERGENCY! DualShield AI detected a crash. My live location: https://www.google.com/maps?q=$lat,$lng - Please call me immediately!"

                        if (contacts.isEmpty()) {
                            Log.w("SOS_Dispatch", "No contacts found to notify!")
                            aiStatus = "No Contacts Found! Calling..."
                        } else {
                            var sentCount = 0
                            contacts.forEach { contact ->
                                val phone = contact.contact_phone.trim()
                                if (phone.isNotEmpty()) {
                                    try {
                                        // Use multipart to handle long messages (>160 chars)
                                        val parts = smsManager.divideMessage(message)
                                        if (parts.size == 1) {
                                            smsManager.sendTextMessage(phone, null, message, null, null)
                                        } else {
                                            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                                        }
                                        sentCount++
                                        Log.d("SOS_Dispatch", "SMS dispatched to $phone (${parts.size} part(s))")
                                    } catch (smsEx: Exception) {
                                        Log.e("SOS_Dispatch", "SMS to $phone failed: ${smsEx.message}")
                                    }
                                }
                            }
                            aiStatus = if (sentCount > 0) "SMS Sent to $sentCount Guardian(s). Calling in 5s..."
                                       else "SMS Failed – check contacts!"
                        }
                    } catch (e: Exception) {
                        Log.e("SOS_Dispatch", "SMS block error: ${e.message}")
                        aiStatus = "SMS Error: ${e.message?.take(40)}"
                    }
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
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
                Text("SENTINEL AI: $aiStatus", color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, fontWeight = FontWeight.Black)
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
        Text("CRASH\nDETECTED!", color = MaterialTheme.colorScheme.onBackground, fontSize = 42.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 48.sp)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Assistant is asking: \"Are you safe?\"", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = timeLeft / 30f, modifier = Modifier.fillMaxSize(), color = AlertRedBright, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 8.dp)
            Text(text = "$timeLeft", color = MaterialTheme.colorScheme.onBackground, fontSize = 80.sp, fontWeight = FontWeight.Light)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        SentinelCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SAY \"YES\" TO CANCEL PROTOCOL", color = sentinelGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(if (sosDispatched) "SOS DISPATCHED" else "Emergency signal in $timeLeft seconds", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { dispatchSos() },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(if (sosDispatched) "STAY CALM" else "SEND SOS NOW", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onCancel,
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
