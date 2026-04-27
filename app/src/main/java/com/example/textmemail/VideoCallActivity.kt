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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas

class VideoCallActivity : ComponentActivity() {

    private val appId = "011681283d5843468537b01ee700c0a9"
    private var mRtcEngine: RtcEngine? = null
    
    // El token proporcionado por el usuario
    private val agoraToken = "24079ff642da45658997952972d2b46d"
    
    private val remoteUidState = mutableStateOf<Int?>(null)
    private val isMutedState = mutableStateOf(false)
    private val isVideoEnabledState = mutableStateOf(true)
    private var channelName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""

        if (checkSelfPermission()) {
            initAgora()
        }

        setContent {
            MaterialTheme {
                VideoCallScreen(
                    remoteUid = remoteUidState.value,
                    isMuted = isMutedState.value,
                    isVideoEnabled = isVideoEnabledState.value,
                    onToggleMute = {
                        isMutedState.value = !isMutedState.value
                        mRtcEngine?.muteLocalAudioStream(isMutedState.value)
                    },
                    onToggleVideo = {
                        isVideoEnabledState.value = !isVideoEnabledState.value
                        mRtcEngine?.muteLocalVideoStream(!isVideoEnabledState.value)
                    },
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

    private fun initAgora() {
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

                override fun onError(err: Int) {
                    runOnUiThread {
                        val msg = when(err) {
                            110 -> "Error 110: Token inválido o expirado"
                            101 -> "Error 101: App ID inválido"
                            else -> "Error Agora: $err"
                        }
                        Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            mRtcEngine = RtcEngine.create(config)
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
            
            // Usamos el nuevo token
            mRtcEngine?.joinChannel(agoraToken, channelName, 0, options)
            
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun checkSelfPermission(): Boolean {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 22)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 22 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initAgora()
        }
    }

    private fun leaveChannel() {
        mRtcEngine?.stopPreview()
        mRtcEngine?.leaveChannel()
        RtcEngine.destroy()
        finish()
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
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (remoteUid != null) {
            AndroidView(
                factory = { ctx -> 
                    RtcEngine.CreateRendererView(ctx).apply { setupRemoteVideo(this, remoteUid) }
                },
                update = { view -> setupRemoteVideo(view, remoteUid) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Conectando con el usuario...", color = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 40.dp, end = 16.dp)
                .size(width = 110.dp, height = 160.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.DarkGray)
                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
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
                Icon(Icons.Default.VideocamOff, null, Modifier.align(Alignment.Center), tint = Color.White)
            }
        }

        Surface(
            modifier = Modifier
                .padding(bottom = 40.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(32.dp)),
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleMute, modifier = Modifier.clip(CircleShape).background(if (isMuted) Color.Red else Color.White.copy(0.2f))) {
                    Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
                }
                FloatingActionButton(onClick = onEndCall, containerColor = Color.Red, contentColor = Color.White, shape = CircleShape) {
                    Icon(Icons.Default.CallEnd, null)
                }
                IconButton(onClick = onToggleVideo, modifier = Modifier.clip(CircleShape).background(if (!isVideoEnabled) Color.Red else Color.White.copy(0.2f))) {
                    Icon(if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, null, tint = Color.White)
                }
            }
        }
    }
    BackHandler { onEndCall() }
}
