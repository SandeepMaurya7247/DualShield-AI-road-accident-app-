package com.team404.dualshield.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun DualShieldNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val emergencyManager = remember { EmergencyManager(context) }

    // Use session to determine start destination
    val startDest = if (UserSession.isLoggedIn(context)) "home" else "login"

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "map", "contacts", "settings")

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
        containerColor = BgDark
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
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
                        onCancel = { navController.popBackStack() },
                        onTimeUp = {
                            navController.navigate("home") { 
                                popUpTo("home")
                            }
                        }
                    )
                }
                // ── Advanced Routes ──────────────────────────────────────────
                composable("settings") {
                     SettingsScreen(
                        userName = UserSession.getName(context),
                        userPhone = UserSession.getPhone(context),
                        onBack = { navController.popBackStack() },
                        onLogout = {
                            UserSession.clear(context)
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                        },
                        onNavigateToAdvanced = { navController.navigate("advanced_hub") }
                    )
                }
                composable("advanced_hub") {
                    AdvancedHubScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToStatus = { navController.navigate("system_status") },
                        onNavigateToSensors = { navController.navigate("sensors") },
                        onNavigateToModel = { navController.navigate("ml_model") },
                        onNavigateToSosProtocol = { navController.navigate("sos_sandbox") },
                        onNavigateToGeofence = { navController.navigate("geofence_registry") }
                    )
                }
                composable("system_status") { SystemStatusScreen(onBack = { navController.popBackStack() }) }
                composable("sensors") { SensorMonitorScreen(onBack = { navController.popBackStack() }) }
                composable("ml_model") { MlModelStatusScreen(onBack = { navController.popBackStack() }) }
                composable("sos_sandbox") { SosProtocolScreen(onBack = { navController.popBackStack() }) }
                composable("geofence_registry") { GeofencingManagerScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
fun CustomBottomNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF141416), // BgDark
        contentColor = TextWhite,
        tonalElevation = 0.dp,
        modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
            label = { Text("HOME") },
            selected = currentRoute == "home",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TextWhite,
                selectedTextColor = TextWhite,
                unselectedIconColor = TextGrayDark,
                unselectedTextColor = TextGrayDark,
                indicatorColor = CardDark
            ),
            onClick = { onNavigate("home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Explore, contentDescription = "Map") }, // Close enough icon to map pin/compass
            label = { Text("MAP") },
            selected = currentRoute == "map",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TextWhite,
                selectedTextColor = TextWhite,
                unselectedIconColor = TextGrayDark,
                unselectedTextColor = TextGrayDark,
                indicatorColor = CardDark
            ),
            onClick = { onNavigate("map") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Group, contentDescription = "Contacts") },
            label = { Text("CONTACTS") },
            selected = currentRoute == "contacts",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TextWhite,
                selectedTextColor = TextWhite,
                unselectedIconColor = TextGrayDark,
                unselectedTextColor = TextGrayDark,
                indicatorColor = CardDark
            ),
            onClick = { onNavigate("contacts") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("SETTINGS") },
            selected = currentRoute == "settings",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TextWhite,
                selectedTextColor = TextWhite,
                unselectedIconColor = TextGrayDark,
                unselectedTextColor = TextGrayDark,
                indicatorColor = CardDark
            ),
            onClick = { onNavigate("settings") }
        )
    }
}
