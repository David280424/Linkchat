// app/src/main/java/ui_chat/ChatScreen.kt
package ui_chat

import android.Manifest
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import auth.ChatManager
import com.example.textmemail.R
import com.example.textmemail.VideoCallActivity
import com.example.textmemail.models.Contact
import com.example.textmemail.models.Message
import com.example.textmemail.ui_chat.MessageBubble
import kotlinx.coroutines.delay
import java.io.File

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

    var showCallingDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    // Grabación Audio
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val onlineLabel = stringResource(R.string.online)
    val offlineLabel = stringResource(R.string.offline)
    val typeMsgPlaceholder = stringResource(R.string.type_message)
    val forwardLabel = stringResource(R.string.message_forwarded)
    val cancelLabel = stringResource(R.string.cancel)
    val deleteLabel = stringResource(R.string.delete)
    val forwardBtnLabel = stringResource(R.string.forward)
    val optionsTitle = stringResource(R.string.message_options)

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { ChatManager.sendMediaMessage(contact.uid, it, "image") { _, _ -> } }
    }

    DisposableEffect(contact.uid) {
        val regMessages = ChatManager.listenForMessages(contact.uid) { newList ->
            messages.clear()
            messages.addAll(newList)
        }
        onDispose {
            regMessages.remove()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun startCall() {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val channelName = if (currentUserUid <= contact.uid) "${currentUserUid}_${contact.uid}" else "${contact.uid}_${currentUserUid}"
        ChatManager.startCallSignal(contact.uid, channelName)
        showCallingDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(contact.name.ifBlank { contact.email }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (contact.isOnline) onlineLabel else offlineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (contact.isOnline) Color(0xFF00BFA5) else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { startCall() }) { Icon(Icons.Default.Videocam, contentDescription = null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().background(Color(0xFFF0F2F5))) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(message = msg, isMe = msg.senderId == FirebaseAuth.getInstance().currentUser?.uid) {
                        selectedMessageForOptions = msg
                    }
                }
            }

            Surface(tonalElevation = 8.dp, color = Color.White) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAddMenu = true }) { 
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray) 
                    }
                    
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(typeMsgPlaceholder) },
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
                    
                    if (input.isBlank()) {
                        IconButton(onClick = {
                            if (!isRecording) {
                                isRecording = true
                                audioFile = File(context.cacheDir, "record.aac")
                                recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
                                recorder?.apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(audioFile?.absolutePath)
                                    prepare()
                                    start()
                                }
                            } else {
                                isRecording = false
                                try { recorder?.stop() } catch (e: Exception) {}
                                recorder?.release()
                                recorder = null
                                audioFile?.let { ChatManager.sendMediaMessage(contact.uid, Uri.fromFile(it), "audio") { _, _ -> } }
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = if(isRecording) Color.Red else Color.Gray)
                        }
                    } else {
                        FloatingActionButton(
                            onClick = {
                                val text = input.trim()
                                if (text.isNotEmpty()) {
                                    ChatManager.sendMessage(contact.uid, text) { _, _ -> }
                                    input = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            containerColor = Color(0xFF673AB7),
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) { Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }

    if (showCallingDialog) {
        OutgoingCallScreen(
            contact = contact,
            onHangUp = {
                ChatManager.endCallSignal(contact.uid)
                showCallingDialog = false
            }
        )
        
        LaunchedEffect(Unit) {
            delay(4000)
            val intent = Intent(context, VideoCallActivity::class.java).apply {
                val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                putExtra("CHANNEL_NAME", if (currentUserUid <= contact.uid) "${currentUserUid}_${contact.uid}" else "${contact.uid}_${currentUserUid}")
                putExtra("TOKEN", "")
            }
            context.startActivity(intent)
            showCallingDialog = false
        }
    }

    if (showAddMenu) {
        ModalBottomSheet(onDismissRequest = { showAddMenu = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ListItem(
                    headlineContent = { Text("Galería") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                    modifier = Modifier.clickable { galleryLauncher.launch("image/*"); showAddMenu = false }
                )
            }
        }
    }

    if (selectedMessageForOptions != null) {
        AlertDialog(
            onDismissRequest = { selectedMessageForOptions = null },
            confirmButton = {
                TextButton(onClick = { showForwardDialog = true }) { 
                    Text(forwardBtnLabel, fontWeight = FontWeight.Bold) 
                }
            },
            dismissButton = {
                val isMe = selectedMessageForOptions?.senderId == FirebaseAuth.getInstance().currentUser?.uid
                Row {
                    if (isMe) {
                        TextButton(onClick = {
                            ChatManager.deleteMessage(contact.uid, selectedMessageForOptions!!.id) { _, _ -> }
                            selectedMessageForOptions = null
                        }) { Text(deleteLabel, color = Color.Red, fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = { selectedMessageForOptions = null }) { Text(cancelLabel) }
                }
            },
            title = { Text(optionsTitle) },
            text = { Text(selectedMessageForOptions?.text ?: "") }
        )
    }

    if (showForwardDialog && selectedMessageForOptions != null) {
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
                                ChatManager.sendMessage(c.uid, "[$forwardLabel]: ${selectedMessageForOptions!!.text}") { _, _ -> }
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
                }) { Text(cancelLabel) }
            }
        )
    }
}

@Composable
fun OutgoingCallScreen(
    contact: Contact,
    onHangUp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        RippleAnimation()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = Color(0xFF673AB7),
                shadowElevation = 12.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).ifBlank { contact.email.take(1) }.uppercase(),
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = contact.name.ifBlank { contact.email },
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            PulsingText("Llamando...")
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            FloatingActionButton(
                onClick = onHangUp,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Colgar",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun RippleAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleDelays = listOf(0, 700, 1400)

    rippleDelays.forEach { delay ->
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2100, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2100, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha"
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(Color(0xFF673AB7).copy(alpha = 0.2f), CircleShape)
        )
    }
}

@Composable
fun PulsingText(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseText")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaText"
    )
    Text(
        text = text,
        color = Color.White.copy(alpha = alpha),
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium
    )
}
