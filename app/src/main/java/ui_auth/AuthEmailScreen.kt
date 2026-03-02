// Interfaz compose para login y registro de usuario
package com.example.textmemail.ui_auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthEmailScreen(
    onRegister: (name: String, email: String, password: String, language: String, done: (Boolean, String) -> Unit) -> Unit,
    onLogin: (email: String, password: String, done: (Boolean, String) -> Unit) -> Unit,
    // NUEVO: se llama cada vez que el usuario toca ES/EN en Registrar
    onLanguageChanged: (String) -> Unit = {}
) {
    var mode by remember { mutableStateOf(AuthMode.Login) }

    var name by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("es") } // "es" | "en"
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val canSubmit = if (mode == AuthMode.Login) {
        email.isNotBlank() && pass.isNotBlank() && !isLoading
    } else {
        name.isNotBlank() && email.isNotBlank() && pass.length >= 6 && pass == confirm && !isLoading
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (mode == AuthMode.Login) "Iniciar sesión" else "Crear cuenta",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))

        if (mode == AuthMode.Register) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Selector de idioma con cambio inmediato
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Idioma:")
                FilterChip(
                    selected = language == "es",
                    onClick = {
                        language = "es"
                        onLanguageChanged("es")   // << cambia UI + guarda en DataStore fuera
                    },
                    label = { Text("ES") }
                )
                FilterChip(
                    selected = language == "en",
                    onClick = {
                        language = "en"
                        onLanguageChanged("en")
                    },
                    label = { Text("EN") }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Correo electrónico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        imageVector = if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPass) "Ocultar" else "Mostrar"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (mode == AuthMode.Register) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirmar contraseña") },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                isError = confirm.isNotEmpty() && confirm != pass,
                supportingText = {
                    if (confirm.isNotEmpty() && confirm != pass) {
                        Text("No coincide con la contraseña")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                isLoading = true
                message = null
                if (mode == AuthMode.Login) {
                    onLogin(email, pass) { ok, msg ->
                        isLoading = false
                        message = msg
                    }
                } else {
                    onRegister(name, email, pass, language) { ok, msg ->
                        isLoading = false
                        message = msg
                    }
                }
            },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (mode == AuthMode.Login) "Entrar" else "Registrarse")
        }

        TextButton(
            onClick = {
                mode = if (mode == AuthMode.Login) AuthMode.Register else AuthMode.Login
                message = null
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                if (mode == AuthMode.Login) "¿No tienes cuenta? Crear una"
                else "¿Ya tienes cuenta? Inicia sesión"
            )
        }

        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}

private enum class AuthMode { Login, Register }