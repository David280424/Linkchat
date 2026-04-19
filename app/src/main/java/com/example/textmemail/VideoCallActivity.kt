// app/src/main/java/com/example/textmemail/VideoCallActivity.kt
package com.example.textmemail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas

class VideoCallActivity : ComponentActivity() {

    private val appId = "011681283d5843468537b01ee700c0a9"
    private var mRtcEngine: RtcEngine? = null
    
    // State managed by the Activity to ensure lifecycle consistency with Agora
    private val remoteUidState = mutableStateOf<Int?>(null)
    private val isMutedState = mutableStateOf(false)
    private val isVideoEnabledState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mantener pantalla encendida y extender a barra de estado
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val channelName = intent.getStringExtra("CHANNEL_NAME") ?: run {
            Toast.makeText(this, "Canal no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val token = intent.getStringExtra("TOKEN") ?: ""

        if (checkSelfPermission()) {
            initAgora(channelName, token)
        }

        setContent {
            MaterialTheme {
                VideoCallScreen(
                    remoteUid = remoteUidState.value,
                    isMuted = isMutedState.value,
                    isVideoEnabled = isVideoEnabledState.value,
                    onToggleMute = { toggleMute() },
                    onToggleVideo = { toggleVideo() },
                    onEndCall = { leaveChannel() },
                    setupLocalVideo = { view -> 
                        mRtcEngine?.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0)) 
                    },
                    setupRemoteVideo = { view, uid -> 
                        mRtcEngine?.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid)) 
                    }
                )
            }
        }
    }

    private fun initAgora(channelName: String, token: String) {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    remoteUidState.value = uid
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    remoteUidState.value = null
                }

                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    println("Conectado exitosamente al canal: $channel")
                }
            }
            mRtcEngine = RtcEngine.create(config)
            mRtcEngine?.enableVideo()
            
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            }
            mRtcEngine?.joinChannel(if (token.isEmpty()) null else token, channelName, 0, options)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error inicializando Agora", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun toggleMute() {
        isMutedState.value = !isMutedState.value
        mRtcEngine?.muteLocalAudioStream(isMutedState.value)
    }

    private fun toggleVideo() {
        isVideoEnabledState.value = !isVideoEnabledState.value
        mRtcEngine?.muteLocalVideoStream(!isVideoEnabledState.value)
    }

    private fun leaveChannel() {
        mRtcEngine?.leaveChannel()
        RtcEngine.destroy()
        mRtcEngine = null
        finish()
    }

    private fun checkSelfPermission(): Boolean {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        return if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, 22)
            false
        } else true
    }

    override fun onDestroy() {
        super.onDestroy()
        mRtcEngine?.leaveChannel()
        RtcEngine.destroy()
    }
}

@Composable
fun VideoCallScreen(
    remoteUid: Int?,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onEndCall: () -> Unit,
    setupLocalVideo: (SurfaceView) -> Unit,
    setupRemoteVideo: (SurfaceView, Int) -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Pantalla Remota (Fondo completo)
        if (remoteUid != null) {
            AndroidView(
                factory = { ctx ->
                    RtcEngine.CreateRendererView(ctx).apply { setupRemoteVideo(this, remoteUid) }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Esperando al otro usuario...", color = Color.Gray)
            }
        }

        // Pantalla Local (Miniatura flotante)
        Box(
            modifier = Modifier
                .padding(top = 40.dp, end = 16.dp)
                .size(width = 120.dp, height = 180.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.DarkGray)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            if (isVideoEnabled) {
                AndroidView(
                    factory = { ctx ->
                        RtcEngine.CreateRendererView(ctx).apply { 
                            setZOrderMediaOverlay(true)
                            setupLocalVideo(this) 
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.White)
                }
            }
        }

        // Controles Inferiores (Estilo moderno)
        Surface(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(32.dp)),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    isActive = !isMuted,
                    onClick = onToggleMute
                )
                
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Terminar")
                }

                CallControlButton(
                    icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    isActive = isVideoEnabled,
                    onClick = onToggleVideo
                )
            }
        }
    }

    BackHandler { onEndCall() }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isActive) Color.White.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.8f))
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}
