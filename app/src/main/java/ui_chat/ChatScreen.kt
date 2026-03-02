// app/src/main/java/com/example/textmemail/ui_chat/ChatScreen.kt
package com.example.textmemail.ui_chat

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import auth.ChatManager
import com.example.textmemail.VideoCallActivity
import com.example.textmemail.models.Contact
import com.example.textmemail.models.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contact: Contact,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<Message>() }
    var input by remember { mutableStateOf("") }

    // Listener en tiempo real
    DisposableEffect(contact.uid) {
        val reg = ChatManager.listenForMessages(contact.uid) { newList ->
            messages.clear()
            messages.addAll(newList)
        }
        onDispose { reg.remove() }
    }

    // Función para iniciar videollamada
    fun startVideoCall() {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        println("🎥 INICIANDO VIDEOLLAMADA:")
        println("   - Usuario actual: $currentUserUid")
        println("   - Contacto UID: ${contact.uid}")
        
        // Generar nombre de canal único y consistente
        val channelName = if (currentUserUid <= contact.uid) {
            "${currentUserUid}_${contact.uid}"
        } else {
            "${contact.uid}_${currentUserUid}"
        }
        
        println("   - Canal generado: $channelName")
        
        val intent = Intent(context, VideoCallActivity::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("TOKEN", "") // Sin token - proyecto en modo test
        }
        
        println("   - Lanzando VideoCallActivity...")
        context.startActivity(intent)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(contact.name.ifBlank { contact.email }) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                IconButton(onClick = { startVideoCall() }) {
                    Icon(Icons.Default.Videocam, contentDescription = "Videollamada")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { msg ->
                val isMe = msg.senderId == FirebaseAuth.getInstance().currentUser?.uid
                MessageBubble(message = msg, isMe = isMe)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Escribe un mensaje...") },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        ChatManager.sendMessage(contact.uid, text) { ok, err ->
                            if (!ok) println("Error enviando mensaje: $err")
                        }
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Enviar")
            }
        }
    }
}