package com.example.textmemail.ui_auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthPhoneScreen(
    onSendCode: (String, (Boolean, String?) -> Unit) -> Unit,
    onVerifyCode: (String, String, String, String, String, Boolean, (Boolean, String) -> Unit) -> Unit,
    onQuickAdminLogin: (done: (Boolean, String) -> Unit) -> Unit,
    onForgotPassword: (String, (Boolean, String) -> Unit) -> Unit,
    onLanguageChanged: (String) -> Unit = {}
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) } // 1: Datos, 2: SMS, 3: Reset
    
    var phoneNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("es") }
    
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF6200EE), Color(0xFF3700B3)))))

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
                    
                    if (step == 1) {
                        TabRow(selectedTabIndex = if (isRegisterMode) 1 else 0, containerColor = Color.Transparent, divider = {}) {
                            Tab(selected = !isRegisterMode, onClick = { isRegisterMode = false; message = null }) {
                                Text("Entrar", modifier = Modifier.padding(8.dp), fontWeight = if(!isRegisterMode) FontWeight.Bold else FontWeight.Normal)
                            }
                            Tab(selected = isRegisterMode, onClick = { isRegisterMode = true; message = null }) {
                                Text("Registrarse", modifier = Modifier.padding(8.dp), fontWeight = if(isRegisterMode) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    Icon(
                        imageVector = if (step == 2) Icons.Default.Security else if (step == 3) Icons.Default.Email else Icons.Default.Person,
                        contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF6200EE)
                    )
                    
                    Text(
                        if (step == 2) "Verificar Teléfono" else if (step == 3) "Recuperar" else if(isRegisterMode) "Crea tu cuenta" else "Bienvenido",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(16.dp))

                    if (step == 1) {
                        if (isRegisterMode) {
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre Completo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo Electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña de respaldo") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = { Text("Número de Teléfono") }, placeholder = { Text("+52...") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                        
                        if (!isRegisterMode) {
                            TextButton(onClick = { step = 3 }, modifier = Modifier.align(Alignment.End)) { Text("¿Olvidaste tu contraseña?", fontSize = 12.sp) }
                        }
                    } else if (step == 2) {
                        Text("Enviamos un SMS a $phoneNumber", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = verificationCode, onValueChange = { verificationCode = it }, label = { Text("Código de 6 dígitos") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    } else {
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo registrado") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            message = null
                            when (step) {
                                1 -> {
                                    if (phoneNumber.lowercase().trim() == "admin") {
                                        onQuickAdminLogin { ok, msg -> isLoading = false; message = msg }
                                    } else {
                                        onSendCode(phoneNumber) { success, error ->
                                            isLoading = false
                                            if (success) step = 2 else message = error
                                        }
                                    }
                                }
                                2 -> {
                                    onVerifyCode(verificationCode, name, email, password, language, isRegisterMode) { ok, msg ->
                                        isLoading = false
                                        message = msg
                                    }
                                }
                                3 -> {
                                    onForgotPassword(email) { ok, msg ->
                                        isLoading = false
                                        message = msg
                                        if (ok) step = 1
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && phoneNumber.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text(if(step == 1) "Continuar" else if (step == 2) "Verificar" else "Enviar enlace")
                    }

                    if (step > 1) {
                        TextButton(onClick = { step = 1; message = null }) { Text("Volver") }
                    }
                }
            }
            message?.let { Text(it, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}
