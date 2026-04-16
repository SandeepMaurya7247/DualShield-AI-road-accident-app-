@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.team404.dualshield.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.team404.dualshield.api.UserSession
import com.team404.dualshield.emergency.EmergencyManager
import com.team404.dualshield.ui.screens.*
import kotlinx.coroutines.launch
import com.team404.dualshield.ui.theme.*

@Composable
fun DualShieldNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val emergencyManager = remember { EmergencyManager(context) }

    // Use session to determine start destination
    val startDest = if (UserSession.isLoggedIn(context)) "home" else "login"

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "map", "profile")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                CustomBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            NavHost(navController = navController, startDestination = startDest) {
                composable("login") {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    })
                }
                composable("home") {
                    val phone = UserSession.getPhone(context)
                    HomeScreen(
                        userPhone = phone,
                        userName = UserSession.getName(context),
                        onNavigateToCountdown = { navController.navigate("countdown") },
                        onNavigateToHistory = { navController.navigate("history") }
                    )
                }
                composable("history") {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }
                composable("map") { MapScreen() }
                composable("contacts") {
                    ContactsScreen(
                        userPhone = UserSession.getPhone(context),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("countdown") {
                    CountdownScreen(
                        userId = UserSession.getUserId(context),
                        userPhone = UserSession.getPhone(context),
                        onCancel = { 
                            navController.navigate("home") {
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        },
                        onTimeUp = {
                            navController.navigate("home") { 
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        }
                    )
                }
                // ── User Profile Route (Container for Settings & Contacts) ──
                composable("profile") {
                     var emAlerts by remember { mutableStateOf<Boolean>(UserSession.isEmergencyAlertsEnabled(context)) }
                     var beSync by remember { mutableStateOf<Boolean>(UserSession.isBackendSyncEnabled(context)) }

                     SettingsScreen(
                        userName = UserSession.getName(context),
                        userPhone = UserSession.getPhone(context),
                        emergencyAlerts = emAlerts,
                        onEmergencyAlertsChange = { 
                            emAlerts = it
                            UserSession.setEmergencyAlerts(context, it)
                        },
                        backendSync = beSync,
                        onBackendSyncChange = {
                            beSync = it
                            UserSession.setBackendSync(context, it)
                        },
                        onBack = { navController.popBackStack() },
                        onLogout = {
                            UserSession.clear(context)
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                        },
                        onNavigateToAdvanced = { navController.navigate("advanced_hub") },
                        onNavigateToContacts = { navController.navigate("contacts") },
                        onNavigateToHistory = { navController.navigate("history") }
                    )
                }
                composable("advanced_hub") {
                    AdvancedHubScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToStatus = { navController.navigate("system_status") },
                        onNavigateToSensors = { navController.navigate("sensors") }
                    )
                }
                composable("system_status") { SystemStatusScreen(onBack = { navController.popBackStack() }) }
                composable("sensors") { SensorMonitorScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
fun CustomBottomNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.2f),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = strokeWidth
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple("home", Icons.Default.Home, "HOME"),
                Triple("map", Icons.Default.Map, "MAP"),
                Triple("profile", Icons.Default.Person, "PROFILE")
            )

            items.forEach { (route, icon, label) ->
                val isSelected = currentRoute == route
                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(route) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
