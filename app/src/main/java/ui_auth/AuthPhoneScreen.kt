package com.example.textmemail.ui_auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthPhoneScreen(
    onSendCode: (String, (Boolean, String?) -> Unit) -> Unit,
    onVerifyCode: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onQuickAdminLogin: (done: (Boolean, String) -> Unit) -> Unit,
    onLanguageChanged: (String) -> Unit = {}
) {
    var step by remember { mutableIntStateOf(1) }
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("es") }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF6200EE), Color(0xFF3700B3))
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (step == 1) Icons.Default.Phone else Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF6200EE)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        if (step == 1) "¡Bienvenido!" else "Verificación Real",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                    
                    Text(
                        if (step == 1) "Ingresa tu número para recibir un código de Google" else "Introduce el código de 6 dígitos que recibiste por SMS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    if (step == 1) {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Número de Teléfono") },
                            placeholder = { Text("+52...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Tu nombre") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Idioma:", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            FilterChip(selected = language == "es", onClick = { language = "es"; onLanguageChanged("es") }, label = { Text("ES") })
                            Spacer(Modifier.width(8.dp))
                            FilterChip(selected = language == "en", onClick = { language = "en"; onLanguageChanged("en") }, label = { Text("EN") })
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it },
                            label = { Text("Código de Seguridad") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            message = null
                            if (step == 1 && phoneNumber.lowercase().trim() == "admin") {
                                onQuickAdminLogin { ok, msg -> isLoading = false; message = msg }
                            } else if (step == 1) {
                                onSendCode(phoneNumber) { success, errorMsg ->
                                    isLoading = false
                                    if (success) {
                                        step = 2
                                        message = "SMS enviado. Revisa tu bandeja de entrada."
                                    } else {
                                        message = errorMsg
                                    }
                                }
                            } else {
                                onVerifyCode(verificationCode, name, language) { ok, msg ->
                                    isLoading = false
                                    message = msg
                                }
                            }
                        },
                        enabled = !isLoading && (if(step==1) phoneNumber.isNotBlank() else verificationCode.isNotBlank() && name.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                if (step == 1 && phoneNumber.lowercase().trim() == "admin") "Entrar como Admin" 
                                else if(step == 1) "Recibir SMS Real" 
                                else "Verificar y Entrar", 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (step == 2) {
                        TextButton(onClick = { step = 1; message = null; verificationCode = "" }) {
                            Text("Cambiar número", color = Color(0xFF6200EE))
                        }
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
