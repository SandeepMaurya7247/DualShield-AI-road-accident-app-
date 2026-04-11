package com.team404.dualshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team404.dualshield.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String,
    userPhone: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToAdvanced: () -> Unit
) {
    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Black, color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Section
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(64.dp).background(AccentBlue.copy(0.1f), CircleShape).border(1.dp, AccentBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(userName.take(1).uppercase(), color = AccentBlueLight, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(userName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(userPhone, color = TextGray, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("PREFERENCES", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(start = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(Icons.Default.Notifications, "Emergency Alerts", "Toggle SOS sound and vibration")
            SettingsItem(Icons.Default.CloudSync, "Backend Sync", "Automatic incident reporting")
            SettingsItem(Icons.Default.PrivacyTip, "Data Privacy", "Manage location sharing logs")

            Spacer(modifier = Modifier.height(24.dp))
            Text("DEVELOPER TOOLS", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(start = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))

            AdvancedCard("🛠️ Advanced Control Center", "Diagnostics and engineering tools", onNavigateToAdvanced)

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.05f)),
                shape = RoundedCornerShape(100.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRedBright.copy(0.3f))
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = AlertRedBright)
                Spacer(modifier = Modifier.width(10.dp))
                Text("LOGOUT ACCOUNT", color = AlertRedBright, fontWeight = FontWeight.Black)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("DualShield AI v1.2.0 • Sentinel Edition", color = TextGrayDark, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(CardDark, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = AccentBlueLight, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(desc, color = TextGray, fontSize = 12.sp)
        }
        Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue))
    }
}
