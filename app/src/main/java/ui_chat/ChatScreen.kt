// app/src/main/java/com/example/textmemail/ui_chat/ChatScreen.kt
package com.example.textmemail.ui_chat

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import auth.ChatManager
import com.example.textmemail.R
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
    val listState = rememberLazyListState()

    DisposableEffect(contact.uid) {
        val reg = ChatManager.listenForMessages(contact.uid) { newList ->
            messages.clear()
            messages.addAll(newList)
        }
        onDispose { reg.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
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
                        Text(contact.name.ifBlank { contact.email }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (contact.isOnline) stringResource(R.string.online) else stringResource(R.string.offline),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (contact.isOnline) Color(0xFF00BFA5) else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { startVideoCall() }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Videocall")
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
                .imePadding()
                .background(Color(0xFFF0F2F5))
        ) {
            LazyColumn(
                state = listState,
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

            Surface(tonalElevation = 8.dp, color = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray) }
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.type_message)) },
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
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) { Icon(Icons.Default.Send, contentDescription = stringResource(R.string.send), modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }

    // DIÁLOGOS DE OPCIONES
    if (selectedMessageForOptions != null) {
        AlertDialog(
            onDismissRequest = { selectedMessageForOptions = null },
            confirmButton = {
                TextButton(onClick = { showForwardDialog = true }) { 
                    Text(stringResource(R.string.forward), fontWeight = FontWeight.Bold) 
                }
            },
            dismissButton = {
                val isMe = selectedMessageForOptions?.senderId == FirebaseAuth.getInstance().currentUser?.uid
                Row {
                    if (isMe) {
                        TextButton(onClick = {
                            ChatManager.deleteMessage(contact.uid, selectedMessageForOptions!!.id) { _, _ -> }
                            selectedMessageForOptions = null
                        }) { Text(stringResource(R.string.delete), color = Color.Red, fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = { selectedMessageForOptions = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
            title = { Text(stringResource(R.string.message_options)) },
            text = { Text(selectedMessageForOptions?.text ?: "") }
        )
    }

    if (showForwardDialog && selectedMessageForOptions != null) {
        // CORRECCIÓN: Obtenemos el texto fuera del onClick
        val forwardLabel = context.getString(R.string.message_forwarded)
        AlertDialog(
            onDismissRequest = { 
                showForwardDialog = false
                selectedMessageForOptions = null
            },
            title = { Text(stringResource(R.string.forward_to)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(allContacts) { c ->
                        ListItem(
                            headlineContent = { Text(c.name.ifBlank { c.email }) },
                            modifier = Modifier.clickable {
                                ChatManager.sendMessage(c.uid, "[$forwardLabel]: ${selectedMessageForOptions?.text ?: ""}") { _, _ -> }
                                showForwardDialog = false
                                selectedMessageForOptions = null
                                Toast.makeText(context, forwardLabel, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showForwardDialog = false
                    selectedMessageForOptions = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
