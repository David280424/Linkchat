// app/src/main/java/com/example/textmemail/ui_chat/MessageBubble.kt
package com.example.textmemail.ui_chat

import android.media.MediaPlayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.textmemail.models.Message
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    onLongClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = timeFormat.format(Date(message.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = if (isMe) Color(0xFFDCF8C6) else Color.White,
                contentColor = Color.Black,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 16.dp
                ),
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    when (message.mediaType) {
                        "image" -> {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .sizeIn(maxWidth = 260.dp, maxHeight = 300.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        "audio" -> {
                            AudioPlayer(url = message.mediaUrl ?: "")
                        }
                        else -> {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    ) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Light
                            )
                        )
                        
                        if (isMe) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.isRead) Color(0xFF34B7F1) else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayer(url: String) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }
    
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer.stop()
                mediaPlayer.reset()
                isPlaying = false
            } else {
                try {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(url)
                    mediaPlayer.prepareAsync()
                    mediaPlayer.setOnPreparedListener {
                        it.start()
                        isPlaying = true
                    }
                    mediaPlayer.setOnCompletionListener {
                        isPlaying = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF673AB7)
            )
        }
        Text("Audio Mensaje", fontSize = 14.sp)
    }
}
