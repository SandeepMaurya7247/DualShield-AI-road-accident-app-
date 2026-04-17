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

    // Form fields
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regEmergencyName by remember { mutableStateOf("") }
    var regEmergencyPhone by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Sentinel Brand Header ────────────────────────────────────
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.team404.dualshield.R.drawable.logo),
                contentDescription = "DualShield Logo",
                modifier = Modifier.size(160.dp)
            )

            Text(
                "DualShield AI",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.offset(y = (-30).dp) // Ultra-tight alignment with logo
            )

            Spacer(modifier = Modifier.height(0.dp))

            // ── Unified Registration Form ────────────────────────────────
            Text("Protective Profile", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Text("Enter details to enable automatic crash detection", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(32.dp))

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
                label = "10-digit Mobile Number",
                icon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Emergency Contact Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                Text("EMERGENCY GUARDIAN", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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

            Spacer(modifier = Modifier.height(32.dp))

            DsButton(
                text = "GET STARTED",
                isLoading = isLoading,
                onClick = {
                    if (regName.isBlank() || regPhone.isBlank() || regEmergencyPhone.isBlank()) {
                        errorMessage = "All fields are required for maximum safety."
                        return@DsButton
                    }
                    if (regPhone.length < 10) {
                        errorMessage = "Please enter a valid 10-digit mobile number."
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
                            if (resp.isSuccessful || resp.code() == 201 || resp.code() == 202) {
                                val body = resp.body()
                                UserSession.save(
                                    context = context,
                                    userId = body?.user_id ?: regPhone.trim(),
                                    name = regName.trim(),
                                    phone = regPhone.trim(),
                                    initialEmergencyName = regEmergencyName.trim(),
                                    initialEmergencyPhone = regEmergencyPhone.trim()
                                )
                                onLoginSuccess()
                            } else {
                                // Fallback to Standalone Mode if server returns error
                                UserSession.save(
                                    context = context,
                                    userId = "LOCAL_${regPhone.trim()}",
                                    name = regName.trim(),
                                    phone = regPhone.trim(),
                                    initialEmergencyName = regEmergencyName.trim(),
                                    initialEmergencyPhone = regEmergencyPhone.trim()
                                )
                                onLoginSuccess()
                            }
                        } catch (e: Exception) {
                            // Fallback to Standalone Mode if connectivity fails
                            UserSession.save(
                                context = context,
                                userId = "LOCAL_${regPhone.trim()}",
                                name = regName.trim(),
                                phone = regPhone.trim(),
                                initialEmergencyName = regEmergencyName.trim(),
                                initialEmergencyPhone = regEmergencyPhone.trim()
                            )
                            onLoginSuccess()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage, color = AlertRedBright, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer removed for simplicity as per user's recent edits
            Spacer(modifier = Modifier.height(16.dp))
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

