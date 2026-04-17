package com.team404.dualshield.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.api.LoginRequest
import com.team404.dualshield.api.RegisterRequest
import com.team404.dualshield.api.UserSession
import com.team404.dualshield.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val api = remember { BackendApi.create() }

    var selectedTab by remember { mutableStateOf(0) } // 0=Login, 1=Register

    // Login fields
    var loginPhone by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    // Register fields
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regEmergencyName by remember { mutableStateOf("") }
    var regEmergencyPhone by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tactical Background Layer
        TacticalGrid()
        // Decorative background arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top left glow arc
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(AccentBlue.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 400f
                ),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(-200f, -200f),
                size = androidx.compose.ui.geometry.Size(500f, 500f)
            )
            // Bottom right glow arc
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(AccentGreen.copy(alpha = 0.1f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = 500f
                ),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(size.width - 300f, size.height - 300f),
                size = androidx.compose.ui.geometry.Size(500f, 500f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // ── Sentinel Brand Header ────────────────────────────────────
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.team404.dualshield.R.drawable.logo),
                contentDescription = "DualShield Logo",
                modifier = Modifier.size(150.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "DualShield AI",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                "Intelligent Accident Protection",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Tab Switcher ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("LOGIN", "REGISTER").forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) AccentBlue else Color.Transparent,
                        animationSpec = tween(300),
                        label = "tabBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(300),
                        label = "tabText"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .clickable { selectedTab = index; errorMessage = "" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Form Fields ───────────────────────────────────────────────
            if (selectedTab == 0) {
                // LOGIN FORM
                Text("Welcome back", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Text("Sign in to continue protection", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))

                DsTextField(
                    value = loginPhone,
                    onValueChange = { loginPhone = it },
                    label = "Phone Number",
                    icon = Icons.Default.Phone,
                    keyboardType = KeyboardType.Phone
                )
                Spacer(modifier = Modifier.height(12.dp))
                DsTextField(
                    value = loginPassword,
                    onValueChange = { loginPassword = it },
                    label = "Password",
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    passwordVisible = loginPasswordVisible,
                    onPasswordToggle = { loginPasswordVisible = !loginPasswordVisible }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Forgot password?",
                    color = AccentBlueLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(28.dp))

                DsButton(
                    text = "LOGIN",
                    isLoading = isLoading,
                    onClick = {
                        if (loginPhone.isBlank()) {
                            errorMessage = "Please enter your phone number."
                            return@DsButton
                        }
                        isLoading = true
                        errorMessage = ""
                        coroutineScope.launch {
                            try {
                                val resp = api.loginUser(LoginRequest(loginPhone.trim()))
                                if (resp.isSuccessful) {
                                    val body = resp.body()
                                    if (body != null && body.status == "success") {
                                        val finalName = body.name ?: "User"
                                        UserSession.save(context, body.user_id ?: "uid", finalName, loginPhone.trim())
                                        
                                        // ── Sync All Data to Server ──
                                        try {
                                            api.syncUserData(com.team404.dualshield.api.SyncRequest(
                                                phone = loginPhone.trim(),
                                                name = finalName,
                                                contacts = UserSession.getContactsList(context)
                                            ))
                                        } catch (e: Exception) {
                                            android.util.Log.e("LoginSync", "Sync failed: ${e.message}")
                                        }
                                        
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = "Phone not registered. Please register first."
                                    }
                                } else if (resp.code() == 404) {
                                    errorMessage = "Phone not registered. Please register first."
                                } else {
                                    errorMessage = "Mission Control is unreachable (Error Code: ${resp.code()}). Please check your connection."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Network Fault: Unable to reach the Registry. Ensure your server is active."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

            } else {
                // REGISTER FORM
                Text("Create Account", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Text("Join the Sentinel Network", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))

                DsTextField(
                    value = regName,
                    onValueChange = { regName = it },
                    label = "Full Name",
                    icon = Icons.Default.Person
                )
                Spacer(modifier = Modifier.height(12.dp))
                DsTextField(
                    value = regPhone,
                    onValueChange = { regPhone = it },
                    label = "Phone Number",
                    icon = Icons.Default.Phone,
                    keyboardType = KeyboardType.Phone
                )
                Spacer(modifier = Modifier.height(12.dp))
                DsTextField(
                    value = regPassword,
                    onValueChange = { regPassword = it },
                    label = "Password",
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    passwordVisible = regPasswordVisible,
                    onPasswordToggle = { regPasswordVisible = !regPasswordVisible }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Emergency Contact Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                    Text("EMERGENCY CONTACT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.height(16.dp))

                DsTextField(
                    value = regEmergencyName,
                    onValueChange = { regEmergencyName = it },
                    label = "Emergency Contact Name",
                    icon = Icons.Default.FavoriteBorder
                )
                Spacer(modifier = Modifier.height(12.dp))
                DsTextField(
                    value = regEmergencyPhone,
                    onValueChange = { regEmergencyPhone = it },
                    label = "Emergency Contact Number",
                    icon = Icons.Default.ContactPhone,
                    keyboardType = KeyboardType.Phone
                )

                Spacer(modifier = Modifier.height(28.dp))

                DsButton(
                    text = "CREATE ACCOUNT",
                    isLoading = isLoading,
                    onClick = {
                        if (regName.isEmpty() || regPhone.isEmpty()) {
                            errorMessage = "Name and Phone are required."
                            return@DsButton
                        }
                        isLoading = true
                        errorMessage = ""
                        coroutineScope.launch {
                            try {
                                val resp = api.registerUser(
                                    RegisterRequest(
                                        name = regName.trim(),
                                        phone = regPhone.trim(),
                                        emergency_name = regEmergencyName.trim(),
                                        emergency_phone = regEmergencyPhone.trim()
                                    )
                                )
                                if (resp.isSuccessful || resp.code() == 202) {
                                    val body = resp.body()
                                    val finalName = regName.trim()
                                    val finalPhone = regPhone.trim()
                                    val eName = regEmergencyName.trim()
                                    val ePhone = regEmergencyPhone.trim()

                                    UserSession.save(
                                        context = context,
                                        userId = body?.user_id ?: "uid",
                                        name = finalName,
                                        phone = finalPhone,
                                        initialEmergencyName = eName,
                                        initialEmergencyPhone = ePhone
                                    )
                                    
                                    // ── Sync Registry Data to Server on Register ──
                                    try {
                                        api.syncUserData(com.team404.dualshield.api.SyncRequest(
                                            phone = finalPhone,
                                            name = finalName,
                                            contacts = UserSession.getContactsList(context)
                                        ))
                                    } catch (e: Exception) {
                                        android.util.Log.e("RegisterSync", "Initial sync failed: ${e.message}")
                                    }
                                    
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Registration Failed: Server returned an error (${resp.code()})."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Connection Failure: Unable to transmit registry data. Check your internet."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage, color = AlertRedBright, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                Text("End-to-end encrypted  •  Your data stays private", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Reusable Components ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = AccentBlueLight, modifier = Modifier.size(20.dp))
        },
        trailingIcon = if (isPassword && onPasswordToggle != null) {
            {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = AccentBlueLight,
            focusedIndicatorColor = AccentBlue,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLabelColor = AccentBlueLight,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    )
}

@Composable
fun DsButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                )
            )
            .clickable(enabled = !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

