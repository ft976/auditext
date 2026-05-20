package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PROVIDERS
import com.example.ui.theme.*
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StudioViewModel,
    onBack: () -> Unit
) {
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    
    var keyInput by remember { mutableStateOf(apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = SoftWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SoftWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        },
        containerColor = CharcoalBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text("Active AI Provider", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = provider,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalSurfaceVariant,
                            unfocusedContainerColor = CharcoalSurfaceVariant,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(CharcoalSurface)
                    ) {
                        PROVIDERS.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p, color = SoftWhite) },
                                onClick = {
                                    viewModel.setProvider(p)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                if (provider != "Native (Offline)") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Note: For this template, all providers currently fallback to Native Rendering. An API Key field is provided for actual implementation.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                Text("API Key Setup", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("$provider API Key", color = OffWhite) },
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = OffWhite) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CharcoalSurfaceVariant,
                        unfocusedContainerColor = CharcoalSurfaceVariant,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.saveApiKey(keyInput) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("Save Key")
                }
            }
        }
    }
}
