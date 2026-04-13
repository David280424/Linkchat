// app/src/main/java/com/example/textmemail/ui_chat/ChatScreen.kt
package com.example.textmemail.ui_chat

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    allContacts: List<Contact> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<Message>() }
    var input by remember { mutableStateOf("") }
    
    var selectedMessageForOptions by remember { mutableStateOf<Message?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }

    DisposableEffect(contact.uid) {
        val reg = ChatManager.listenForMessages(contact.uid) { newList ->
            messages.clear()
            messages.addAll(newList)
        }
        onDispose { reg.remove() }
    }

    fun startVideoCall() {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val channelName = if (currentUserUid <= contact.uid) "${currentUserUid}_${contact.uid}" else "${contact.uid}_${currentUserUid}"
        val intent = Intent(context, VideoCallActivity::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("TOKEN", "")
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(contact.name.ifBlank { "Usuario" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("En línea", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00BFA5))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { startVideoCall() }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Videollamada")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF0F2F5))
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isMe = msg.senderId == FirebaseAuth.getInstance().currentUser?.uid
                    MessageBubble(
                        message = msg, 
                        isMe = isMe,
                        onLongClick = { selectedMessageForOptions = msg }
                    )
                }
            }

            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Adjuntar */ }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray)
                    }
                    
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Escribe un mensaje...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFF0F2F5),
                            unfocusedContainerColor = Color(0xFFF0F2F5)
                        )
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    FloatingActionButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                ChatManager.sendMessage(contact.uid, text) { _, _ -> }
                                input = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        containerColor = Color(0xFF673AB7),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (selectedMessageForOptions != null) {
        AlertDialog(
            onDismissRequest = { selectedMessageForOptions = null },
            confirmButton = {
                TextButton(onClick = { 
                    showForwardDialog = true
                }) { 
                    Text("REENVIAR", fontWeight = FontWeight.Bold) 
                }
            },
            dismissButton = {
                val isMe = selectedMessageForOptions?.senderId == FirebaseAuth.getInstance().currentUser?.uid
                Row {
                    if (isMe) {
                        TextButton(onClick = {
                            val msgId = selectedMessageForOptions?.id ?: ""
                            if (msgId.isNotEmpty()) {
                                ChatManager.deleteMessage(contact.uid, msgId) { _, _ -> }
                            }
                            selectedMessageForOptions = null
                        }) { Text("ELIMINAR", color = Color.Red, fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = { selectedMessageForOptions = null }) { Text("CANCELAR") }
                }
            },
            title = { Text("Opciones del mensaje") },
            text = { Text(selectedMessageForOptions?.text ?: "") }
        )
    }

    if (showForwardDialog && selectedMessageForOptions != null) {
        AlertDialog(
            onDismissRequest = { 
                showForwardDialog = false
                selectedMessageForOptions = null
            },
            title = { Text("Reenviar a...") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(allContacts) { c ->
                        ListItem(
                            headlineContent = { Text(c.name.ifBlank { c.email }) },
                            modifier = Modifier.clickable {
                                ChatManager.sendMessage(c.uid, "[Reenviado]: ${selectedMessageForOptions!!.text}") { _, _ -> }
                                showForwardDialog = false
                                selectedMessageForOptions = null
                                Toast.makeText(context, "Mensaje reenviado", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showForwardDialog = false
                    selectedMessageForOptions = null
                }) { Text("CANCELAR") }
            }
        )
    }
}
