package com.example.textmemail.ui_auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.textmemail.models.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    users: List<Contact>,       
    onDeleteUser: (Contact, (Boolean, String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }
    var userToDelete by remember { mutableStateOf<Contact?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialogo de confirmacion antes de eliminar
    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Eliminar usuario") },
            text = { Text("¿Seguro que quieres eliminar a ${userToDelete!!.name.ifBlank { userToDelete!!.email }}? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = userToDelete!!
                        userToDelete = null
                        isLoading = true
                        onDeleteUser(target) { ok, msg ->
                            isLoading = false
                            message = msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { userToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel de Administrador") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Usuarios registrados (${users.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }

            if (users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay usuarios registrados")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users) { user ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        user.name.ifBlank { "(Sin nombre)" },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "UID: ${user.uid.take(12)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { userToDelete = user }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar usuario",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}