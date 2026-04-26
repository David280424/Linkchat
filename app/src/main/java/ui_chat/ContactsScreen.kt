// app/src/main/java/com/example/textmemail/ui_chat/ContactsScreen.kt
package com.example.textmemail.ui_chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.textmemail.R
import com.example.textmemail.models.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onBack: () -> Unit,
    onOpenChat: (Contact) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_users_available))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(contacts) { contact ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Información del contacto
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name.ifBlank { "Sin nombre" },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = contact.email, // Aquí estamos guardando el número o email
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }

                                // Botón de Chat
                                IconButton(onClick = { onOpenChat(contact) }) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Chat",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Botón de Llamada Telefónica
                                IconButton(onClick = {
                                    val number = contact.email.filter { it.isDigit() || it == '+' }
                                    if (number.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                                        context.startActivity(intent)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Llamar",
                                        tint = Color(0xFF4CAF50)
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
