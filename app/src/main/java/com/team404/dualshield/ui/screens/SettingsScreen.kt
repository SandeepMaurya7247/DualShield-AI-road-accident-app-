package com.team404.dualshield.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team404.dualshield.api.UserSession
import com.team404.dualshield.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String,
    userPhone: String,
    emergencyAlerts: Boolean = true,
    onEmergencyAlertsChange: (Boolean) -> Unit = {},
    backendSync: Boolean = true,
    onBackendSyncChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToContacts: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val themeColor = sentinelGreen
    val bgColor = MaterialTheme.colorScheme.background

    val context = androidx.compose.ui.platform.LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(userName) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("MISSION PROFILE", fontWeight = FontWeight.Black, fontSize = 18.sp, color = sentinelGreen) },
            text = {
                Column {
                    Text("REGISTRY ID: ${userPhone}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Sentinel Alias") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = sentinelGreen)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        UserSession.updateName(context, editedName)
                        showInfoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = sentinelGreen)
                ) {
                    Text("UPDATE REGISTRY")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("ABORT", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Professional Cinematic Header (Kept Green as per user request)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(bottomStart = 60.dp, bottomEnd = 60.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(sentinelGreen, sentinelGreen.copy(0.6f))
                        )
                    )
            ) {
                // Subtle Tactical HUD Overlay
                TacticalGrid(gridColor = Color.White.copy(alpha = 0.08f))

                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 48.dp, start = 16.dp).background(Color.White.copy(0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    // Premium Glowing Avatar Container
                    Box(contentAlignment = Alignment.Center) {
                        // Outer Halo Glow
                        Surface(
                            modifier = Modifier.size(150.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.05f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.1f))
                        ) {}
                        
                        // Inner Glowing Ring
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.1f),
                            border = androidx.compose.foundation.BorderStroke(2.dp, sentinelGreen.copy(0.4f))
                        ) {}

                        // Profile Icon Surface
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.onSurface.copy(0.05f)),
                            shadowElevation = 10.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = userName.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 60.sp,
                                        color = sentinelBlue.copy(alpha = 0.9f),
                                        letterSpacing = 0.sp
                                    )
                                )
                                
                                // Integrated HUD Status Dot (Bottom-Right)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                                        .padding(2.dp)
                                        .background(sentinelGreen, CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f), CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // User Identity Section
                    Text(
                        editedName.uppercase(Locale.ROOT), 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Black, 
                        letterSpacing = 1.sp
                    )
                    Text(
                        userPhone, 
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f), 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.Medium, 
                        letterSpacing = 1.sp
                    )
                }
            }

            // Categories
            Column(modifier = Modifier.padding(24.dp)) {
                
                Text("ACCOUNT", color = themeColor, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(16.dp))

                ProfileCard(isAccount = true) {
                    ProfileItem(
                        icon = Icons.Default.ManageAccounts, 
                        title = "Personal Information",
                        onClick = { showInfoDialog = true }
                    )
                    Divider(color = sentinelGreen.copy(0.1f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
                    ProfileItem(
                        icon = Icons.Default.Shield, 
                        title = "Emergency SOS Guardian", 
                        onClick = onNavigateToContacts
                    )
                    Divider(color = sentinelGreen.copy(0.1f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
                    
                    var isSyncing by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    val api = remember { com.team404.dualshield.api.BackendApi.create() }

                    ProfileItem(
                        icon = Icons.Default.Sync,
                        title = if (isSyncing) "Syncing..." else "Sync Profile with Server",
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                scope.launch {
                                    try {
                                        api.syncUserData(com.team404.dualshield.api.SyncRequest(
                                            phone = userPhone,
                                            name = userName,
                                            contacts = UserSession.getContactsList(context)
                                        ))
                                        android.util.Log.d("SettingsSync", "Manual sync success")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SettingsSync", "Manual sync fail: ${e.message}")
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                ProfileCard(isAccount = false) {
                    ProfileItem(
                        icon = Icons.Default.HistoryEdu, 
                        title = "History", 
                        onClick = onNavigateToHistory
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(1.dp, sentinelRed.copy(0.3f), RoundedCornerShape(24.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = sentinelRed.copy(0.05f)),
                    shape = RoundedCornerShape(24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = sentinelRed, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("LOG OUT", color = sentinelRed, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onNavigateToAdvanced, modifier = Modifier.fillMaxWidth()) {
                    Text("ADVANCED MISSION TOOLS", color = themeColor.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
fun ProfileCard(isAccount: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = sentinelBlue.copy(0.04f), // Match the light tint from Advanced Control
        border = androidx.compose.foundation.BorderStroke(1.dp, sentinelBlue.copy(0.12f))
    ) {
        Column { content() }
    }
}

@Composable
fun ProfileIconBox(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(12.dp),
        color = sentinelBlue.copy(0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, sentinelBlue.copy(0.2f))
    ) {
        Icon(icon, contentDescription = null, tint = sentinelBlue, modifier = Modifier.padding(10.dp))
    }
}

@Composable
fun ProfileItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String = "", onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileIconBox(icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = sentinelGreen.copy(0.4f))
    }
}
