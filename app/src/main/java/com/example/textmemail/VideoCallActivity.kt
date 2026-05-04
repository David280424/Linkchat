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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas

class VideoCallActivity : ComponentActivity() {

    private val appId = "011681283d5843468537b01ee700c0a9"
    private var mRtcEngine: RtcEngine? = null
    private val agoraToken = "24079ff642da45658997952972d2b46d"
    
    private val remoteUidState = mutableStateOf<Int?>(null)
    private val isRemoteVideoEnabled = mutableStateOf(true)
    private val isMutedState = mutableStateOf(false)
    private val isVideoEnabledState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""

        if (checkSelfPermission()) {
            initAgora(channelName)
        }

        setContent {
            MaterialTheme {
                VideoCallScreen(
                    remoteUid = remoteUidState.value,
                    isRemoteVideoEnabled = isRemoteVideoEnabled.value,
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

    private fun initAgora(channelName: String) {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    runOnUiThread { remoteUidState.value = uid }
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    runOnUiThread { remoteUidState.value = null }
                }
                override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
                    // Detectar si el otro apagó su cámara
                    runOnUiThread {
                        isRemoteVideoEnabled.value = (state != Constants.REMOTE_VIDEO_STATE_STOPPED)
                    }
                }
            }
            mRtcEngine = RtcEngine.create(config)
            mRtcEngine?.enableAudio()
            mRtcEngine?.setEnableSpeakerphone(true)
            mRtcEngine?.enableVideo()
            mRtcEngine?.startPreview()
            
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = true
                autoSubscribeAudio = true
                autoSubscribeVideo = true
            }
            mRtcEngine?.joinChannel(agoraToken, channelName, 0, options)
        } catch (e: Exception) { finish() }
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
        finish()
    }

    private fun checkSelfPermission(): Boolean {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, perms[0]) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, perms[1]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, perms, 22)
            return false
        }
        return true
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
    isRemoteVideoEnabled: Boolean,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onEndCall: () -> Unit,
    setupLocalVideo: (SurfaceView) -> Unit,
    setupRemoteVideo: (SurfaceView, Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        // VIDEO REMOTO (Fondo)
        if (remoteUid != null && isRemoteVideoEnabled) {
            AndroidView(
                factory = { ctx -> RtcEngine.CreateRendererView(ctx).apply { setupRemoteVideo(this, remoteUid) } },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fondo oscuro bonito si no hay video remoto
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFF000000)))
            ), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountCircle, null, Modifier.size(120.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text(if (remoteUid == null) "Conectando..." else "Cámara desactivada", color = Color.White)
                }
            }
        }

        // VIDEO LOCAL (Miniatura)
        Box(
            modifier = Modifier
                .padding(top = 50.dp, end = 20.dp)
                .size(110.dp, 160.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.DarkGray)
                .border(2.dp, Color.White.copy(0.3f), RoundedCornerShape(16.dp))
        ) {
            if (isVideoEnabled) {
                AndroidView(
                    factory = { ctx -> RtcEngine.CreateRendererView(ctx).apply { setZOrderMediaOverlay(true); setupLocalVideo(this) } },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VideocamOff, null, tint = Color.White.copy(0.5f))
                }
            }
        }

        // BARRA DE CONTROLES
        Surface(
            modifier = Modifier.padding(bottom = 50.dp).align(Alignment.BottomCenter).clip(RoundedCornerShape(35.dp)),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(25.dp)) {
                IconButton(onClick = onToggleMute, modifier = Modifier.clip(CircleShape).background(if(isMuted) Color.Red else Color.White.copy(0.1f))) {
                    Icon(if(isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
                }
                FloatingActionButton(onClick = onEndCall, containerColor = Color.Red, shape = CircleShape) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White)
                }
                IconButton(onClick = onToggleVideo, modifier = Modifier.clip(CircleShape).background(if(!isVideoEnabled) Color.Red else Color.White.copy(0.1f))) {
                    Icon(if(isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, null, tint = Color.White)
                }
            }
        }
    }
    BackHandler { onEndCall() }
}
