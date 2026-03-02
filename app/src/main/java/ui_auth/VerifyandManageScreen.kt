//Interfaz posterior a registro para la validación del email

package com.example.textmemail.ui_auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VerifyAndManageScreen(
    currentEmail: String,
    currentLanguage: String,
    onResendVerification: (done: (Boolean, String) -> Unit) -> Unit,
    onCheckVerified: (done: (Boolean) -> Unit) -> Unit,
    onUpdateLanguage: (String, (Boolean, String) -> Unit) -> Unit,
    onUpdateEmail: (String, (Boolean, String) -> Unit) -> Unit,
    onPasswordReset: (String, (Boolean, String) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    var language by remember { mutableStateOf(currentLanguage) }
    var newEmail by remember { mutableStateOf(currentEmail) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Verificación de correo", style = MaterialTheme.typography.headlineSmall)
        Text("Te enviamos un correo de verificación. Hasta que verifiques, no podrás continuar.")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                isLoading = true
                onResendVerification { ok, msg ->
                    isLoading = false
                    message = msg
                }
            }) { Text("Reenviar verificación") }

            OutlinedButton(onClick = {
                isLoading = true
                onCheckVerified { verified ->
                    isLoading = false
                    message = if (verified) "¡Correo verificado! Ya puedes continuar." else "Aún no verificado."
                }
            }) { Text("Ya verifiqué") }
        }

        Divider()

        Text("Preferencia de idioma", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = language == "es", onClick = { language = "es" }, label = { Text("ES") })
            FilterChip(selected = language == "en", onClick = { language = "en" }, label = { Text("EN") })
            Button(onClick = {
                isLoading = true
                onUpdateLanguage(language) { ok, msg ->
                    isLoading = false
                    message = msg
                }
            }) { Text("Guardar idioma") }
        }

        Divider()

        Text("Correo", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = newEmail,
            onValueChange = { newEmail = it },
            label = { Text("Nuevo correo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                isLoading = true
                onUpdateEmail(newEmail) { ok, msg ->
                    isLoading = false
                    message = msg
                }
            }) { Text("Actualizar correo") }

            OutlinedButton(onClick = {
                isLoading = true
                onPasswordReset(newEmail) { ok, msg ->
                    isLoading = false
                    message = msg
                }
            }) { Text("Restablecer contraseña") }
        }

        Divider()

        Button(onClick = onSignOut) { Text("Cerrar sesión") }

        if (message != null) {
            Text(message!!, color = MaterialTheme.colorScheme.primary)
        }
        if (isLoading) {
            CircularProgressIndicator()
        }
    }
}