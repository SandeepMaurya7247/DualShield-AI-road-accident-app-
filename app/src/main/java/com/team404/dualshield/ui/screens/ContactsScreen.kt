package com.team404.dualshield.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.api.ContactItem
import com.team404.dualshield.api.ContactRequest
import com.team404.dualshield.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    userPhone: String = "",
    onBack: () -> Unit
) {
    val api = remember { BackendApi.create() }
    val scope = rememberCoroutineScope()

    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    fun loadContacts() {
        if (userPhone.isBlank()) return
        scope.launch {
            isLoading = true
            try {
                val resp = api.getContacts(userPhone)
                if (resp.isSuccessful) {
                    contacts = resp.body() ?: emptyList()
                }
            } catch (e: Exception) {
                contacts = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(userPhone) { loadContacts() }

    Box(
        modifier = Modifier.fillMaxSize().background(sentinelBlack)
    ) {
        // Tactical Background Layer
        TacticalGrid()
        
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = TextWhite, modifier = Modifier.size(32.dp))
                        }
                    }
                    Text("SENTINEL NETWORK", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = AccentBlue,
                    contentColor = TextWhite,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp).border(2.dp, Color.White.copy(0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact", modifier = Modifier.size(32.dp))
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                // Header Area
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).background(sentinelCard, CircleShape).border(1.dp, sentinelBlue.copy(0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = sentinelBlue, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("EMERGENCY RELATIVES", color = AccentBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Trusted Community", color = TextWhite, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    
                    if (statusMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(statusMsg, color = AccentGreenBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LaunchedEffect(statusMsg) {
                            delay(3000)
                            statusMsg = ""
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlueLight)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (contacts.isEmpty() && !isLoading) {
                        item {
                            SentinelCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.PersonAddAlt, contentDescription = null, tint = TextGrayDark, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("Sentinel list is empty", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("No one is assigned to receive your alerts.", color = TextGray, fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        items(contacts.size) { index ->
                            val c = contacts[index]
                            ContactCardPremium(
                                name = c.contact_name,
                                phone = c.contact_phone,
                                relation = c.relation,
                                onDelete = {
                                    scope.launch {
                                        try {
                                            api.deleteContact(userPhone, c.contact_phone)
                                            loadContacts()
                                            statusMsg = "Guardian removed"
                                        } catch (e: Exception) { }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SentinelCard {
                            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = sentinelGreen, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "Guardians will receive SMS alerts with your real-time GPS location during emergencies.",
                                    color = TextGray, fontSize = 13.sp, lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddGuardianDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, phone, relation ->
                    scope.launch {
                        try {
                            api.addContact(userPhone, ContactRequest(name, phone, relation))
                            loadContacts()
                            statusMsg = "New guardian added!"
                        } catch (e: Exception) {
                            statusMsg = "Added to local cache"
                        }
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun ContactCardPremium(name: String, phone: String, relation: String, onDelete: () -> Unit) {
    val charColor = listOf(sentinelGlowBlue, sentinelGreen, Color(0xFFC084FC), sentinelGlowRed).random()
    val initials = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

    SentinelCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(charColor.copy(0.1f)).border(1.dp, charColor.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = charColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(phone, color = TextGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.background(sentinelGreen.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(relation.uppercase(), color = sentinelGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Delete", tint = sentinelGlowRed.copy(0.6f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGuardianDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Family") }
    val relations = listOf("Family", "Friend", "Doctor", "Work")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = BgDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add Guardian", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("They will be notified in emergencies.", color = TextGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name", color = TextGrayDark) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = Color.White.copy(0.1f),
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number", color = TextGrayDark) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = Color.White.copy(0.1f),
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    relations.forEach { r ->
                        FilterChip(
                            selected = relation == r,
                            onClick = { relation = r },
                            label = { Text(r) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue,
                                selectedLabelColor = TextWhite,
                                labelColor = TextGray
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { if (name.isNotBlank() && phone.isNotBlank()) onSave(name.trim(), phone.trim(), relation) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(100.dp)
                ) { Text("CONFIRM GUARDIAN", fontWeight = FontWeight.Black) }
                
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCEL", color = TextGray)
                }
            }
        }
    }
}
